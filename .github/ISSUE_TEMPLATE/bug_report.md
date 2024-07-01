name: Bug report
description: Create a new bug report for us to investigate and fix.
labels: bug
body:
  - type: markdown
    attributes:
      value: |-
        Join [the Discord server](https://discord.gg/ZW4s47Ppw4) for questions and discussions.
        If you would like to discuss new features, please select "Feature request" when creating a new issue instead.
        Alternatively, you can join the Discord server and discuss ideas there.
  - type: checkboxes
    attributes:
      label: Basic Troubleshooting
      description: |-
        Make sure you have checked the following first.
        If you don't think it's applicable to your issue, you may check the box.
      options:
        - label: I have checked for similar issues, and my issue is not a duplicate. (Check closed issues as well)
          required: true
        - label: I have checked for pull requests that may already address my issue.
          required: true
        - label: I am using (and my issue is reproducible on) the latest version of youtube-source.
          required: true
        - label: My issue is reproducible with IPv6 rotation.
          required: true
        - label: I have confirmed that my issue is reproducible with the minimum viable clients (MUSIC, WEB and TVHTML5EMBEDDED)
          required: true
  - type: input
    attributes:
      label: "Version of youtube-source"
      placeholder: "Example: 1.3.0"
    validations:
      required: true
  - type: textarea
    attributes:
      label: "Code Example"
      description: |-
        Provide a minimum viable code sample that we can use to reproduce your issue.
        Exclude any sensitive information, such as IP addresses, tokens, keys, etc.
        If this is not applicable, just write "N/A"
    validations:
      required: true
  - type: textarea
    attributes:
      label: "Exception and Stacktrace"
      description: |-
        If your issue relates to an error thrown by youtube-source, please paste the error here.
        Include the ENTIRE stacktrace if available, as well as JSON dump if one is provided.
        You may redact sensitive information such as IP addresses, tokens, keys etc.
        If this is not applicable, just write "N/A".
    validations:
      required: true
