package dev.kultivator.client.dunia.dimensi.render;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.dimensi.laut.LautNpcEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The people of Laut Tanpa Batas ({@link LautNpcEntity}): the Dragon King on D's own pale-scaled skin, in golden robes,
 * antlers, crown and whiskers, a head taller than men; the palace guard in teal with a plumed helmet; the island hermit
 * in sage under a straw hat; the Penglai immortal in white with a jade crown and long white hair; the merchant in
 * ochre with a cap.
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
			default -> {
				skin(state, entity.look(), (entity.look() & 1) == 1);
				set(state, 0xC07030, Realm.FOUNDATION.ordinal(), Headwear.MERCHANT, 1.0F, -1);
			}
		}
	}
}
