package dev.lavalink.youtube.plugin;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.lava.extensions.youtuberotator.YoutubeIpRotatorSetup;
import com.sedmelluq.lava.extensions.youtuberotator.planner.*;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.IpBlock;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv4Block;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv6Block;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSource;
import dev.lavalink.youtube.clients.ClientOptions;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import dev.lavalink.youtube.clients.skeleton.Client;
import lavalink.server.config.RateLimitConfig;
import lavalink.server.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class YoutubePluginLoader implements AudioPlayerManagerConfiguration {
    private static final Logger log = LoggerFactory.getLogger(YoutubePluginLoader.class);

    private final YoutubeConfig youtubeConfig;
    private final ServerConfig serverConfig;
    private final RateLimitConfig ratelimitConfig;
    private final ClientProvider clientProvider;

    // This entire thing is a hack BTW. Designed to support Lavalink v3 and v4
    // with a single plugin. Totally worth it!
    public YoutubePluginLoader(final YoutubeConfig youtubeConfig,
                               final ServerConfig serverConfig) {
        this.youtubeConfig = youtubeConfig;
        this.serverConfig = serverConfig;
        this.ratelimitConfig = serverConfig.getRatelimit();

        final String providerName = isV4OrNewer()
            ? "ClientProviderV4"
            : "ClientProviderV3";

        ClientProvider provider = null;

        try {
            provider = getClientProvider(providerName);
        } catch (Throwable t) {
            log.error("Failed to initialise ClientProvider class with name {}", providerName, t);
        }

        this.clientProvider = provider;

        try {
            PluginInfo.checkForNewRelease();
        } catch (Throwable ignored) {

        }
    }

    private ClientProvider getClientProvider(String name) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> klass = Class.forName("dev.lavalink.youtube.plugin." + name);
        return (ClientProvider) klass.getDeclaredConstructor().newInstance();
    }

    private boolean isV4OrNewer() {
        try {
            Class.forName("com.sedmelluq.discord.lavaplayer.tools.ThumbnailTools");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private ClientOptions getOptionsForClient(String clientName) {
        Map<String, ClientOptions> clientOptions = youtubeConfig != null ? youtubeConfig.getClientOptions() : null;

        if (clientOptions == null || !clientOptions.containsKey(clientName)) {
            return ClientOptions.DEFAULT;
        }

        return clientOptions.get(clientName);
    }

    private IpBlock getIpBlock(String cidr) {
        if (Ipv4Block.isIpv4CidrBlock(cidr)) {
            return new Ipv4Block(cidr);
        } else if (Ipv6Block.isIpv6CidrBlock(cidr)) {
            return new Ipv6Block(cidr);
        } else {
            throw new RuntimeException("Invalid IP Block '" + cidr + "', make sure to provide a valid CIDR notation");
        }
    }

    private AbstractRoutePlanner getRoutePlanner() {
        if (ratelimitConfig == null) {
            log.debug("No ratelimit config found, skipping setup of route planner");
            return null;
        }

        if (ratelimitConfig.getIpBlocks().isEmpty()) {
            log.info("Ratelimit config present but no IP blocks were specified, route planner will not initialised.");
            return null;
        }

        final List<InetAddress> excluded = new ArrayList<>();

        try {
            for (String s : ratelimitConfig.getExcludedIps()) {
                InetAddress byName = InetAddress.getByName(s);
                excluded.add(byName);
            }
        } catch (UnknownHostException e) {
            log.warn("Failed to get excluded IP", e);
        }

        final Predicate<InetAddress> filter = (ip) -> !excluded.contains(ip);

        final List<IpBlock> ipBlocks = ratelimitConfig.getIpBlocks().stream()
            .map(this::getIpBlock)
            .collect(Collectors.toList());

        final String strategy = ratelimitConfig.getStrategy().toLowerCase(Locale.getDefault());

        switch (strategy) {
            case "rotateonban":
                return new RotatingIpRoutePlanner(ipBlocks, filter, ratelimitConfig.getSearchTriggersFail());
            case "loadbalance":
                return new BalancingIpRoutePlanner(ipBlocks, filter, ratelimitConfig.getSearchTriggersFail());
            case "nanoswitch":
                return new NanoIpRoutePlanner(ipBlocks, ratelimitConfig.getSearchTriggersFail());
            case "rotatingnanoswitch":
                return new RotatingNanoIpRoutePlanner(ipBlocks, filter, ratelimitConfig.getSearchTriggersFail());
            default:
                throw new RuntimeException("Unknown strategy '" + strategy + "'!");
        }
    }

    @Override
    public AudioPlayerManager configure(AudioPlayerManager audioPlayerManager) {
        if (youtubeConfig != null && !youtubeConfig.getEnabled()) {
            return audioPlayerManager;
        }

        final YoutubeAudioSourceManager source;
        final boolean allowSearch = youtubeConfig == null || youtubeConfig.getAllowSearch();
        final boolean allowDirectVideoIds = youtubeConfig == null || youtubeConfig.getAllowDirectVideoIds();
        final boolean allowDirectPlaylistIds = youtubeConfig == null || youtubeConfig.getAllowDirectPlaylistIds();

        if (clientProvider == null) {
            log.warn("ClientProvider instance is missing. The YouTube source will be initialised with default clients.");
            source = new YoutubeAudioSourceManager(allowSearch, allowDirectVideoIds, allowDirectPlaylistIds);
        } else {
            String[] clients;

            if (youtubeConfig == null || youtubeConfig.getClients() == null) {
                log.warn("youtubeConfig missing or 'clients' was not specified, default values will be used.");
                clients = clientProvider.getDefaultClients();
            } else {
                clients = youtubeConfig.getClients();
                Pot pot = youtubeConfig.getPot();

                if (pot != null) {
                    String token = pot.getToken();
                    String visitorData = pot.getVisitorData();

                    if (token != null && visitorData != null) {
                        log.debug("Applying poToken and visitorData to WEB & WEBEMBEDDED client (token: {}, vd: {})", token, visitorData);
                        YoutubeSource.setPoTokenAndVisitorData(token, visitorData);
                    } else if (token != null || visitorData != null) {
                        log.warn("Both pot.token and pot.visitorData must be specified and valid for pot to apply.");
                    }
                }
            }

            source = new YoutubeAudioSourceManager(allowSearch, allowDirectVideoIds, allowDirectPlaylistIds, clientProvider.getClients(clients, this::getOptionsForClient));
        }

        log.info("YouTube source initialised with clients: {} ", Arrays.stream(source.getClients()).map(Client::getIdentifier).collect(Collectors.joining(", ")));
        final AbstractRoutePlanner routePlanner = getRoutePlanner();

        if (routePlanner != null) {
            final int retryLimit = ratelimitConfig.getRetryLimit();
            final YoutubeIpRotatorSetup rotator = new YoutubeIpRotatorSetup(routePlanner)
                .forConfiguration(source.getHttpInterfaceManager(), false)
                .withMainDelegateFilter(source.getContextFilter());

            if (retryLimit == 0) {
                rotator.withRetryLimit(Integer.MAX_VALUE);
            } else if (retryLimit > 0) {
                rotator.withRetryLimit(retryLimit);
            }

            rotator.setup();
        }

        Integer playlistLoadLimit = serverConfig.getYoutubePlaylistLoadLimit();

        if (playlistLoadLimit != null) {
            source.setPlaylistPageCount(playlistLoadLimit);
        }

        if (youtubeConfig != null && youtubeConfig.getOauth() != null) {
            YoutubeOauthConfig oauthConfig = youtubeConfig.getOauth();

            if (oauthConfig.getEnabled()) {
                log.debug("Configuring youtube oauth integration with token: \"{}\" skipInitialization: {}", oauthConfig.getRefreshToken(), oauthConfig.getSkipInitialization());
                source.useOauth2(oauthConfig.getRefreshToken(), oauthConfig.getSkipInitialization());
            }
        }

        audioPlayerManager.registerSourceManager(source);
        return audioPlayerManager;
    }
}
