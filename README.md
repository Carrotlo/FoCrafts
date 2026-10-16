# FoCrafts

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/focrafts) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/focrafts/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Custom crafting recipes with player GUIs and an in-game editor.

*Developed by Carrotio.*

## Features

- Adds configurable custom recipes and a dedicated crafting interface.
- Provides searchable recipe browsing and recipe preview screens.
- Supports custom item inputs, outputs, and recipe definitions.
- Includes an in-game administrator editor.
- Keeps GUI text, messages, sounds, and recipe data configurable.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/focrafts). Check the [versions page](https://modrinth.com/plugin/focrafts/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `Vault`

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/craft` — Opens the custom crafting GUI Aliases: `/focrafts`.
- `/recipes` — Opens the custom recipes GUI Aliases: `/forecipes`.
- `/focraftsadmin <version|editor|reload>` — FoCrafts admin command Aliases: `/craftadmin`.

### Permissions

- `focrafts.use` — Allows using FoCrafts user GUIs Default: `true`.
- `focrafts.admin` — Allows using FoCrafts admin editor Default: `op`.
- `focrafts.recipe.*` — Grants access to all custom recipe permission nodes Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

This plugin does not register public PlaceholderAPI placeholders.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `recipes.yml` — Custom recipe definitions.
- `sounds.yml` — Configurable interaction sounds.
- `dialogs/*.yml` — Native dialog layouts and text.
- `guis/*.yml` — GUI layouts, slots, labels, and lore.

Main config areas: `recipes`, `native-dialogs`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

This plugin does not expose a public developer API.

</details>
