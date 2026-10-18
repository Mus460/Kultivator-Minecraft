package dev.kultivator.client.talisman;

import dev.kultivator.cultivation.VeinCompassItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Client side of P7-kultivasi's talismans, compass and boons (DESIGN 19.3.5, 19.3.6, 19.3.11). The talisman effects are
 * drawn by {@code client/fx/TalismanFx}; the compass needle uses the vanilla compass model property, so the only extra
 * here is the live distance line of the Spirit Vein Compass tooltip (the server tooltip has no player).
 */
public final class TalismanClient {

	public static void init() {
		ItemTooltipCallback.EVENT.register(TalismanClient::addLines);
	}

	private static void addLines(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		if (stack.isEmpty() || !stack.is(VeinCompassItem.ITEM)) {
			return;
		}
		LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
		Minecraft minecraft = Minecraft.getInstance();
		if (tracker == null || tracker.target().isEmpty() || minecraft.player == null || minecraft.level == null) {
			return;
		}
		GlobalPos target = tracker.target().get();
		if (!target.dimension().equals(minecraft.level.dimension())) {
			lines.add(Component.translatable("kultivator.kultivasi.compass.tooltip.other_world").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		Vec3 at = Vec3.atCenterOf(target.pos());
		double dx = at.x - minecraft.player.getX();
		double dz = at.z - minecraft.player.getZ();
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		lines.add(Math.min(lines.size(), 2), Component.translatable("kultivator.kultivasi.compass.tooltip.distance", distance)
				.withStyle(ChatFormatting.AQUA));
	}

	private TalismanClient() {
	}
}
