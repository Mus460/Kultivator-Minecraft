package dev.kultivator.client.dunia.dimensi.render;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.dimensi.DimensiNpc;
import dev.kultivator.dunia.dimensi.laut.LautNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The people of Laut Tanpa Batas ({@link LautNpcEntity}): the Dragon King on D's own pale-scaled skin, in golden robes,
 * antlers, crown and whiskers, a head taller than men, the sea pearl held up in his left hand; the palace guard in teal
 * with a plumed helmet, the trident held ready; the island hermit in sage under a straw hat and the Penglai immortal in
 * white with a jade crown and long white hair, both sitting in meditation while nobody is near; the merchant in ochre
 * with a cap, beckoning; the old fisherman in faded indigo under his bamboo douli, rod in hand, shading his eyes to
 * scan the horizon.
 */
public class LautNpcRenderer extends HumanNpcRenderer<LautNpcEntity> {

	private static final Identifier KING_SKIN = Kultivator.id("textures/dunia/dimensi/entity/raja_naga.png");

	public LautNpcRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void look(LautNpcEntity entity, DiscipleRenderState state) {
		switch (entity.role()) {
			case LautNpcEntity.RAJA_NAGA -> {
				ownSkin(state, KING_SKIN);
				set(state, 0xD8A830, Realm.TRUE_IMMORTAL.ordinal(), Headwear.DRAGON_KING, 1.15F, -1);
			}
			case LautNpcEntity.PENJAGA -> {
				skin(state, entity.look(), false);
				set(state, 0x2F6F78, Realm.NASCENT_SOUL.ordinal(), Headwear.GUARD, 1.05F, -1);
			}
			case LautNpcEntity.PERTAPA -> {
				skin(state, entity.look(), false);
				set(state, 0x8A9A80, Realm.DAO_UNION.ordinal(), Headwear.HERMIT, 0.98F, -1);
			}
			case LautNpcEntity.DEWA_PENGLAI -> {
				skin(state, entity.look(), false);
				set(state, 0xF4F0E6, Realm.TRUE_IMMORTAL.ordinal(), Headwear.PENGLAI, 1.06F, ARGB.color(255, 255, 250, 238));
			}
			case LautNpcEntity.NELAYAN -> {
				skin(state, entity.look(), false);
				set(state, 0x34506A, Realm.QI_REFINING.ordinal(), Headwear.FISHER, 0.97F, -1);
			}
			default -> {
				skin(state, entity.look(), (entity.look() & 1) == 1);
				set(state, 0xC07030, Realm.FOUNDATION.ordinal(), Headwear.MERCHANT, 1.0F, -1);
			}
		}
	}

	@Override
	protected void pose(LautNpcEntity entity, DiscipleRenderState state, boolean talking, boolean still) {
		float age = state.ageInTicks;
		switch (entity.role()) {
			// Ao Guang holds the sea pearl up in his left hand
			case LautNpcEntity.RAJA_NAGA -> {
				if (still) {
					state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
				}
			}
			case LautNpcEntity.PENJAGA -> {
				if (still && !entity.hasFlag(DimensiNpc.FLAG_FIGHT)) {
					state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
				}
			}
			// the fisherman shades his eyes and scans the sea between his talk
			case LautNpcEntity.NELAYAN -> {
				if (!talking && still && Mth.sin(age * 0.04F + entity.getId()) > 0.2F) {
					state.leftArmPose = HumanoidModel.ArmPose.SPYGLASS;
					state.xRot = Math.min(state.xRot, -6.0F);
				}
			}
			// the merchant beckons now and then
			case LautNpcEntity.PEDAGANG -> {
				if (!talking && still && Mth.sin(age * 0.07F + entity.getId()) > 0.75F) {
					state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
				}
			}
			default -> {
			}
		}
	}

	@Override
	protected @Nullable Component title(LautNpcEntity entity) {
		return Component.translatable("kultivator.dimensi.gelar." + LautNpcEntity.roleId(entity.role()));
	}
}
