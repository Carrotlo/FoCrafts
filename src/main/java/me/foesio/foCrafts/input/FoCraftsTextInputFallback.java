package me.foesio.foCrafts.input;

import me.foesio.core.dialog.NativeDialogAvailability;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.dialog.TextInputFallback;
import me.foesio.core.message.FoMessageService;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class FoCraftsTextInputFallback implements TextInputFallback, AutoCloseable {
    private static final String CANCEL_KEYWORD = "cancel";

    private final FoMessageService messages;
    private final Supplier<NativeDialogSupport> supportSupplier;
    private final Map<UUID, ChatSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingHints = new ConcurrentHashMap<>();
    private final Set<UUID> warnedPlayers = ConcurrentHashMap.newKeySet();

    public FoCraftsTextInputFallback(FoMessageService messages, Supplier<NativeDialogSupport> supportSupplier) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.supportSupplier = Objects.requireNonNull(supportSupplier, "supportSupplier");
    }

    public void rememberHint(Player player, String hint) {
        pendingHints.put(player.getUniqueId(), Objects.toString(hint, ""));
    }

    public void clearHint(Player player) {
        pendingHints.remove(player.getUniqueId());
    }

    @Override
    public void open(Player player, TextDialogRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        warnPlayerFallback(player);
        sessions.put(player.getUniqueId(), new ChatSession(onSubmit, onCancel));
        String hint = pendingHints.remove(player.getUniqueId());
        messages.send(player, "chat-prompt-start", "{prefix}{muted}{hint}", Map.of("hint", messages.renderTemplate(chatHint(hint, request), Map.of())));
        player.closeInventory();
    }

    public boolean hasPendingChatInput(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void handleChatInput(Player player, String input) {
        ChatSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (input.equalsIgnoreCase(CANCEL_KEYWORD)) {
            if (session.onCancel() != null) {
                session.onCancel().run();
            }
            return;
        }
        if (session.onSubmit() != null) {
            session.onSubmit().accept(input);
        }
    }

    public void clear(Player player) {
        UUID playerId = player.getUniqueId();
        sessions.remove(playerId);
        pendingHints.remove(playerId);
        warnedPlayers.remove(playerId);
    }

    public void reload() {
        sessions.clear();
        pendingHints.clear();
        warnedPlayers.clear();
    }

    @Override
    public void close() {
        reload();
    }

    private void warnPlayerFallback(Player player) {
        NativeDialogSupport support = supportSupplier.get();
        NativeDialogAvailability availability = support.availability();
        if (!availability.configEnabled() || (!availability.runtimeDisabled() && availability.serverSupportsNativeDialogs()) || !support.warnOnFallback()) {
            return;
        }
        if (!player.hasPermission("focrafts.admin")) {
            return;
        }
        if (warnedPlayers.add(player.getUniqueId())) {
            messages.send(player, "native-dialogs-fallback", "{prefix}{bad}Native dialogs are not supported on this server. Using chat input.");
        }
    }

    private String chatHint(String hint, TextDialogRequest request) {
        if (hint != null && !hint.isBlank()) {
            return hint;
        }

        String current = request.initialValue().isBlank() ? "none" : request.initialValue();
        String format = request.placeholder().isBlank() ? request.fieldLabel() : request.placeholder();
        return "{theme}" + request.title()
                + ". Current: {white}" + current
                + "{theme}. Expected: {white}" + format
                + "{theme}. Type {bad}cancel {theme}to cancel.";
    }

    private record ChatSession(Consumer<String> onSubmit, Runnable onCancel) {
    }
}
