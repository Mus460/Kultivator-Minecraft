package dev.kultivator.data;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Data attached to players by the mod. */
public final class ModAttachments {

	/** All of a player's cultivation. Saved with the player and copied to the new player on respawn. */
	public static final AttachmentType<CultivationData> CULTIVATION = AttachmentRegistry.<CultivationData>builder()
			.persistent(CultivationData.CODEC)
			.copyOnDeath()
			.initializer(CultivationData::new)
			.buildAndRegister(Kultivator.id("cultivation"));

	/** What other clients need to draw the player; synced to everyone who can see them (and the player). */
	public static final AttachmentType<VisualState> VISUAL = AttachmentRegistry.<VisualState>builder()
			.initializer(() -> VisualState.NONE)
			.syncWith(VisualState.STREAM_CODEC, AttachmentSyncPredicate.all())
			.buildAndRegister(Kultivator.id("visual"));

	public static void init() {
	}

	private ModAttachments() {
	}
}
