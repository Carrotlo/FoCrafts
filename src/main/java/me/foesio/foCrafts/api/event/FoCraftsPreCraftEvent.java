package me.foesio.foCrafts.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Fired once per affordable craft, before ingredients or costs are taken. Cancellation prevents that craft. */
public final class FoCraftsPreCraftEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String recipeId;
    private final ItemStack result;
    private boolean cancelled;

    public FoCraftsPreCraftEvent(Player player, String recipeId, ItemStack result, boolean asynchronous) {
        super(asynchronous);
        this.player = Objects.requireNonNull(player, "player");
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.result = Objects.requireNonNull(result, "result").clone();
    }

    public Player getPlayer() { return player; }
    public String getRecipeId() { return recipeId; }
    public ItemStack getResult() { return result.clone(); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
