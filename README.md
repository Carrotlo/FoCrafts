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

FoCrafts 2.2 exposes `RecipeCatalog` and `RecipeWriter` through Bukkit's service registry. Compile against the matching FoCrafts jar with `provided` scope (or `compileOnly` in Gradle); do not shade it into a consumer plugin. The source project coordinates are `me.foesio:FoCrafts:2.2`. Obtain the matching jar from this project's build or install it into your local Maven repository; these coordinates do not imply a public Maven repository.

For a required integration, add `depend: [FoCrafts]` to the consumer's `plugin.yml`. For an optional integration, use `softdepend: [FoCrafts]`, check that FoCrafts is enabled before loading a class with direct FoCrafts references, and handle a missing service. Reacquire services after provider disable/enable; a cached service reports unavailable after disable. Paper-plugin consumers need a server dependency on FoCrafts with classpath access enabled.

For an optional consumer, keep direct `me.foesio.foCrafts.api` references in a separate integration class. Only construct that class after `getServer().getPluginManager().isPluginEnabled("FoCrafts")` returns true.

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoCrafts</artifactId>
  <version>2.2</version>
  <scope>provided</scope>
</dependency>
```

```java
RegisteredServiceProvider<RecipeCatalog> registration =
        Bukkit.getServicesManager().getRegistration(RecipeCatalog.class);
if (registration != null) {
    RecipeCatalog catalog = registration.getProvider();
    catalog.recipe("starter_wand").ifPresent(recipe ->
            getLogger().info(recipe.displayName()));
    // Call availability(player, id) on that player's owning server thread.
}
```

`RecipeCatalog.recipes()` and `recipe(id)` return detached data from the currently loaded catalog, including disabled recipes. `RecipeData` copies its result and ingredient stacks on input and output. `availability(player, id)` checks whether the loaded recipe is enabled, has an output, passes its recipe permission, and is allowed in the player's world. It does not check `/craft` entry permission, ingredients, XP, Vault balance, or whether a future craft will succeed. Query and write methods must be called from server event/scheduler execution, never an asynchronous worker; `availability` must run on the player's owning thread.

`RecipeWriter.upsert(RecipeData)` creates or updates a recipe by ID. An enabled recipe requires a valid output and at least one valid ingredient; IDs are letters/numbers with `_` or `-` (1–64 characters). `delete(id)` removes one, including the last recipe without recreating a default on reload. `rename(id, displayName)` changes the display name and generates a unique ID using the admin editor's slug rule, subject to the API ID length limit. The result status is `CREATED`, `UPDATED`, `UNCHANGED`, `DELETED`, `NOT_FOUND`, `INVALID`, `SAVE_FAILED`, or `UNAVAILABLE`. Successful changes are saved to `recipes.yml` before the call returns; a save failure rolls the in-memory API change back. `UNCHANGED` and `NOT_FOUND` do not write. These writes are administrative operations by trusted plugins and do not check a player's permission. They do not fire a recipe-change event.

```java
RegisteredServiceProvider<RecipeWriter> registration =
        Bukkit.getServicesManager().getRegistration(RecipeWriter.class);
if (registration != null) {
    RecipeWriteResult outcome = registration.getProvider().delete("old_recipe");
    getLogger().info(outcome.status().name());
}
```

### Craft events

`FoCraftsPreCraftEvent` is cancellable. It fires once for each affordable matched craft in FoCrafts' crafting GUI, after a cost check but before ingredients or costs are taken. Cancelling it stops that craft without those effects; earlier crafts in the same shift click remain complete. FoCrafts sends no failure message for a cancelled first craft, so a vetoing listener may send its own explanation. The recipe ID and copied result are read-only; changing the returned item does not change the output. FoCrafts rechecks the loaded recipe, eligibility, and cost after listeners return; a recipe changed during the event will not be crafted from a stale definition.

`FoCraftsCraftCompletedEvent` is informational. It fires once per completed craft after ingredients and costs are taken and the result is given or dropped, before configured `on-craft.commands` run. Failed, blocked, and cancelled attempts do not fire it. A shift click that crafts three times fires three completion events. The result getter returns a copy.

Both events are dispatched inline from the inventory click on that player's owning server execution thread. Their Bukkit asynchronous flag follows the source inventory event; listeners should schedule later work according to the server's thread model and must not assume that every regionized server callback is on one global main thread.

```java
@EventHandler
public void onCraft(FoCraftsCraftCompletedEvent event) {
    String recipeId = event.getRecipeId();
    Player player = event.getPlayer();
    // Award one unit of quest progress for this completed craft.
}

@EventHandler
public void beforeCraft(FoCraftsPreCraftEvent event) {
    if (blockedForPlayer(event.getPlayer(), event.getRecipeId())) {
        event.setCancelled(true);
    }
}
```

The event classes and service types are in `me.foesio.foCrafts.api` and `me.foesio.foCrafts.api.event`. Their public signatures contain Bukkit and FoCrafts types, never FoPluginCore types. FoPluginCore is shaded and relocated inside FoCrafts.

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `RecipeCatalog`

```java
List<RecipeData> recipes();
Optional<RecipeData> recipe(String);
RecipeAvailability availability(Player, String);
```

#### `RecipeWriter`

```java
RecipeWriteResult upsert(RecipeData);
RecipeWriteResult delete(String);
RecipeWriteResult rename(String, String);
```

**Other public contract types:** `RecipeAvailability`, `RecipeData`, `RecipeWriteResult`.

### Complete Bukkit event index

- `FoCraftsCraftCompletedEvent`.
- `FoCraftsPreCraftEvent`.

</details>
