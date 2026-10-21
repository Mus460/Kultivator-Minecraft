package dev.kultivator.dunia.kapal;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.harta.HartaText;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Lunas Kapal Roh (DESIGN 34.5.15): used on open ground with room, it launches its ship (docked, hovering over the
 * clicked block, facing where the player looks, turned to the nearest quarter). The ship's hull, fuel, colour and hold
 * ride in the item ({@link KapalItems#STATE}, {@code minecraft:container}) and come back on "Simpan". The captain needs
 * the class's realm and the owner's sect hall level; one ship per owner is out at a time. Nothing is placed: the ship
 * is an entity.
 */
public class LunasItem extends Item {

	private final KapalClass kind;

	public LunasItem(Item.Properties properties, KapalClass kind) {
		super(properties);
		this.kind = kind;
	}

	public KapalClass kind() {
		return kind;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		BlockPos base = context.getClickedPos();
		if (launch(level, player, stack, Vec3.atBottomCenterOf(base.above()), false)) {
			if (!player.isCreative()) {
				stack.shrink(1);
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.FAIL;
	}

	/**
	 * Launches a ship of this keel's class at {@code at} (the ground under it). {@code test}: no realm, hall or one-ship
	 * rule ({@code uji kapal beri} uses a fresh keel and still launches here). True when the ship stands in the world.
	 */
	boolean launch(ServerLevel level, ServerPlayer player, ItemStack stack, Vec3 at, boolean test) {
		KapalConfig cfg = Kapal.config();
		if (!cfg.ships || !DuniaSpine.enabled()) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.off").withColor(0xFF8A7A));
			return false;
		}
		if (!test && !player.isCreative()) {
			Realm realm = Cultivators.get(player).realm();
			if (!realm.atLeast(kind.realm())) {
				Msg.actionBar(player, Component.translatable("kultivator.kapal.need_realm", kind.realm().displayName()).withColor(0xFF8A7A));
				return false;
			}
			if (kind.hallLevel() > 0 && Halls.level(player.getUUID()) < kind.hallLevel()) {
				Msg.actionBar(player, Component.translatable("kultivator.kapal.need_hall", kind.hallLevel()).withColor(0xFF8A7A));
				return false;
			}
		}
		KapalFleet.Entry out = KapalFleet.ship(level.getServer(), player.getUUID());
		if (out != null) {
			Entity existing = level.getServer().getLevel(out.dim()) == null ? null : level.getServer().getLevel(out.dim()).getEntity(out.ship());
			boolean chunkLoaded = level.getServer().getLevel(out.dim()) != null
					&& level.getServer().getLevel(out.dim()).hasChunkAt(out.pos().getX(), out.pos().getZ());
			if (existing == null && chunkLoaded) {
				// its chunk is loaded and it is not there: a stale entry
				KapalFleet.remove(level.getServer(), player.getUUID(), out.ship());
			} else {
				Msg.actionBar(player, Component.translatable("kultivator.kapal.one_ship").withColor(0xFFD54A));
				return false;
			}
		}
		float yaw = KapalRohEntity.snapped(player.getYRot());
		boolean across = Math.abs(yaw) == 90.0F;
		double hx = (across ? kind.length() : kind.width()) * 0.5D;
		double hz = (across ? kind.width() : kind.length()) * 0.5D;
		double y = at.y + KapalRohEntity.HOVER;
		AABB room = new AABB(at.x - hx, y, at.z - hz, at.x + hx, y + Math.min(kind.height(), 10), at.z + hz).deflate(0.25D);
		if (!level.noCollision(room)) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.no_room", Mth.ceil(hx * 2), Mth.ceil(hz * 2)).withColor(0xFFD54A));
			return false;
		}
		KapalRohEntity ship = KapalEntities.ROH.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (ship == null) {
			return false;
		}
		KapalItems.State state = stack.get(KapalItems.STATE);
		ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		ship.setup(kind, player, state, contents, test);
		ship.snapTo(at.x, y, at.z, yaw, 0.0F);
		if (!level.addFreshEntity(ship)) {
			return false;
		}
		KapalFleet.put(level.getServer(), player.getUUID(), new KapalFleet.Entry(ship.getUUID(), level.dimension(), ship.blockPosition(),
				kind.ordinal(), player.getName().getString()));
		level.playSound(null, at.x, y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.9F, 1.3F);
		KapalNet.send(player, new KapalNet.Event(ship.getId(), KapalNet.Event.EV_LAUNCH, at.x, y, at.z, kind.ordinal()));
		Msg.actionBar(player, Component.translatable("kultivator.kapal.launched", Component.translatable("kultivator.kapal.class." + kind.model()))
				.withColor(0x9FE0C0));
		Advancements.grant(player, "dunia/ship_launched");
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("kultivator.kapal.class." + kind.model()).withColor(0xE8D9A8));
		builder.accept(Component.translatable("item.kultivator.lunas.tooltip.use").withColor(HartaText.USE_COLOR));
		builder.accept(Component.translatable("item.kultivator.lunas.tooltip.stats", kind.seats(), Math.round(kind.topSpeed()), kind.hull())
				.withColor(HartaText.USE_COLOR));
		builder.accept(Component.translatable(kind.hallLevel() > 0 ? "item.kultivator.lunas.tooltip.need_hall" : "item.kultivator.lunas.tooltip.need",
				kind.realm().displayName(), kind.hallLevel()).withStyle(ChatFormatting.GRAY));
		KapalItems.State state = stack.get(KapalItems.STATE);
		if (state != null) {
			builder.accept(Component.translatable("item.kultivator.lunas.tooltip.state", state.hull(), kind.hull(), state.fuel() / 1200L)
					.withColor(state.broken() ? 0xFF8A7A : 0x9FE0C0));
		}
		HartaText.sources("kultivator.kapal.kapal", builder);
	}
}
