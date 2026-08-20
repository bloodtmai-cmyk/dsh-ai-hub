# WeCom Office client plugin

The bundled runtime comes from the official [WecomTeam/wecom-cli](https://github.com/WecomTeam/wecom-cli) project. Its MIT notice is preserved in [UPSTREAM_LICENSE.md](UPSTREAM_LICENSE.md); WeCom account and API use remain subject to the service's own terms.

AI Hub managed client plugin backed by the official `@wecom/cli` runtime already shipped with the Harness Enterprise desktop app. The plug-in exposes fixed Tool operations for identity, directory lookup, schedules, meetings, outbound bot-session messaging, documents, sheets, mail, todo, and Drive. It never accepts a command, executable path, or endpoint URL from the model.

Every Tool call first checks the Harness-owned loopback authorization lease. The encrypted lease is bound to the trusted desktop workcode; read access lasts seven days, write confirmation lasts 24 hours, and a desktop restart does not extend or shorten either window. The official CLI's persistent login state is therefore necessary but not sufficient for a managed call.

The official CLI can list recent bot sessions and actively send messages to them. It does not expose inbound message bodies or an auto-reply listener, so authorization alone must not be presented as enabling bot replies.
