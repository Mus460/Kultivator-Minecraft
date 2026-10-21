package dev.kultivator.client.dunia.dimensi.render;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.dimensi.bawah.ArwahEntity;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The people of Dunia Bawah ({@link ArwahEntity}): Meng Po in faded hemp with her white bun and pins; the Ten Kings in
 * the court's colour under the winged black hat; King Yama in black and crimson under the crown board with jade
 * tassels, a head taller; Ox-Head and Horse-Face in dark leather on D's own skins with their beast heads; ghosts in
 * white mourning, see-through and pale; hungry ghosts grey, see-through, with long black hair and burning eyes.
 */
public class ArwahRenderer extends HumanNpcRenderer<ArwahEntity> {

	private static final Identifier OX_SKIN = Kultivator.id("textures/dunia/dimensi/entity/kepala_lembu.png");
	private static final Identifier HORSE_SKIN = Kultivator.id("textures/dunia/dimensi/entity/muka_kuda.png");
	private static final Identifier GHOST_SKIN = Kultivator.id("textures/dunia/dimensi/entity/hantu_lapar.png");
	/** The robes of the Ten Courts (Qinguang ... Zhuanlun). */
	private static final int[] COURT_ROBE = {0x6A1A1A, 0x1E2A5A, 0x2A3A2A, 0x5A1A3A, 0x3A1A0A, 0x2A2A3A, 0x4A2A0A, 0x1A3A4A, 0x3A2A4A, 0x5A4A1A};

	public ArwahRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void look(ArwahEntity entity, DiscipleRenderState state) {
		int role = entity.role();
		int nascent = Realm.NASCENT_SOUL.ordinal();
		switch (role) {
			case ArwahEntity.MENG_PO -> {
				skin(state, 3, true);
				set(state, 0xB8AE98, nascent, Headwear.MENG_PO, 0.9F, ARGB.color(255, 236, 228, 220));
			}
			case ArwahEntity.HAKIM -> {
				skin(state, entity.look(), false);
				set(state, COURT_ROBE[Mth.clamp(entity.variant(), 1, 10) - 1], Realm.DEITY_TRANSFORMATION.ordinal(), Headwear.JUDGE, 1.04F, -1);
			}
			case ArwahEntity.RAJA_YAMA -> {
				skin(state, 1, false);
				set(state, 0x2A0A10, Realm.TRUE_IMMORTAL.ordinal(), Headwear.YAMA, 1.14F, ARGB.color(255, 246, 226, 222));
			}
			case ArwahEntity.KEPALA_LEMBU -> {
				ownSkin(state, OX_SKIN);
				set(state, 0x3A2A1E, Realm.GOLDEN_CORE.ordinal(), Headwear.OX, 1.1F, -1);
			}
			case ArwahEntity.MUKA_KUDA -> {
				ownSkin(state, HORSE_SKIN);
				set(state, 0x22242E, Realm.GOLDEN_CORE.ordinal(), Headwear.HORSE, 1.1F, -1);
			}
			case ArwahEntity.HANTU_LAPAR -> {
				ownSkin(state, GHOST_SKIN);
				state.role = DiscipleEntity.Role.PHANTOM.ordinal();
				float flicker = 0.85F + 0.15F * Mth.sin(state.ageInTicks * 0.3F + entity.getId());
				set(state, 0x3A3444, Realm.QI_REFINING.ordinal(), Headwear.GHOST_HAIR, 1.0F,
						ARGB.color(Math.round(170 * flicker), 150, 140, 170));
				((FabricRenderState) state).setData(HeadwearLayer.GLOW, Headwear.GHOST_EYES);
			}
			default -> {
				skin(state, entity.look(), (entity.look() & 1) == 1);
				state.role = DiscipleEntity.Role.PHANTOM.ordinal();
				set(state, 0xE8E8F0, Realm.QI_REFINING.ordinal(), Headwear.NONE, 1.0F, ARGB.color(140, 200, 220, 255));
			}
		}
	}
}
