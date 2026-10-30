package me.foesio.foCrafts.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Fired once per craft after ingredients, cost, and result delivery, before on-craft commands. */
public final class FoCraftsCraftCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String recipeId;
    private final ItemStack result;

    public FoCraftsCraftCompletedEvent(Player player, String recipeId, ItemStack result, boolean asynchronous) {
        super(asynchronous);
        this.player = Objects.requireNonNull(player, "player");
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.result = Objects.requireNonNull(result, "result").clone();
    }

    public Player getPlayer() { return player; }
    public String getRecipeId() { return recipeId; }
    public ItemStack getResult() { return result.clone(); }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
