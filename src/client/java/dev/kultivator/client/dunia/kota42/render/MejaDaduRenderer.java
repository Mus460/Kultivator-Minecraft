package dev.kultivator.client.dunia.kota42.render;

import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.dunia.kota42.acara.MejaDaduEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:meja_dadu} (DESIGN 42.12.4, 42.9.6 "Guanpu"): the dice table: a dark wooden table with aprons and
 * stretchers, green felt with the 大 (big) and 小 (small) fields, a porcelain dish, a rattan-bound bamboo cup and three
 * ivory dice (one and four in red, the Chinese way). The variant adds its corner: copper coins and a wine cup in a tavern
 * ({@link MejaDaduEntity#VARIAN_KEDAI}), a red lantern on a pole, chip stacks and a 当 sign in the licensed den
 * ({@link MejaDaduEntity#VARIAN_GUANPU}), a patched felt and a candle stub at the Ghost Market
 * ({@link MejaDaduEntity#VARIAN_GELAP}).
 * <p>
 * {@link MejaDaduEntity#KEADAAN_DIAM}: the cup covers the dice. {@link MejaDaduEntity#KEADAAN_KOCOK}: the cup is lifted
 * and shaken hard for {@link MejaDaduEntity#KOCOK_TICK} ticks (dice hidden inside). {@link MejaDaduEntity#KEADAAN_BUKA}:
 * the cup lies tipped beside the dish and the dice tumble for half a second, then settle on the synced faces
 * ({@link MejaDaduEntity#mata}; 0 = still hidden), so every client shows the same throw. Textures {@code dadu.png}
 * (+ {@code _pancar} for the lantern and candle).
 */
public class MejaDaduRenderer extends TemaBendaRenderer<MejaDaduEntity, MejaDaduRenderer.State, KotaModel<MejaDaduRenderer.State>> {

	/** Ticks the dice tumble after the cup is lifted. */
	static final float GULING = 10.0F;
	/** Die scale (the mesh die is 3 px for crisp pips). */
	static final float SKALA_DADU = 0.4F;

	public static class State extends TemaBendaRenderer.State {
		public final int[] mata = new int[3];
	}

	public MejaDaduRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.DADU), MejaDaduRenderer::pose),
				KotaRenderClient.tekstur("dadu"), KotaRenderClient.tekstur("dadu_pancar"), 0.75F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(MejaDaduEntity entity) {
		return 1.0D;
	}

	@Override
	public void extractRenderState(MejaDaduEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		int packed = entity.dadu();
		for (int i = 0; i < 3; i++) {
			state.mata[i] = MejaDaduEntity.mata(packed, i);
		}
		float lampu = state.varian == MejaDaduEntity.VARIAN_KEDAI ? 0.0F : 0.6F + 0.4F * KotaRenderClient.malam(entity.level());
		state.pancar = lampu * KotaRenderClient.kedip(state.waktu, state.fase);
	}

	static void pose(KotaModel<State> m, State s) {
		m.satu("jenis_", 3, Mth.clamp(s.varian, 0, 2));
		ModelPart cawan = m.p("cawan");
		boolean buka = s.keadaan == MejaDaduEntity.KEADAAN_BUKA;
		if (s.keadaan == MejaDaduEntity.KEADAAN_KOCOK) {
			float t = s.umur;
			float k = s.jauh ? 0.0F : 1.0F - KotaModel.maju(t, MejaDaduEntity.KOCOK_TICK - 4, 4.0F);
			cawan.y -= 2.5F + Math.abs(Mth.sin(t * 1.9F)) * 1.6F * k;
			cawan.x += Mth.sin(t * 2.4F) * 1.2F * k;
			cawan.z += Mth.cos(t * 3.1F) * 0.8F * k;
			cawan.zRot = Mth.sin(t * 2.4F + 0.5F) * 0.25F * k;
			cawan.xRot = Mth.cos(t * 3.1F + 0.3F) * 0.2F * k;
		} else if (buka) {
			float t = KotaModel.maju(s.umur, 0.0F, 6.0F);
			cawan.x += 5.5F * t;
			cawan.z += 2.0F * t;
			cawan.y -= Mth.sin(t * Mth.PI) * 3.0F;
			cawan.zRot = -1.75F * t;
		}
		for (int i = 0; i < 3; i++) {
			ModelPart d = m.p("dadu_" + i);
			ModelPart k = m.p("dadu_" + i + "_k");
			int muka = s.mata[i];
			d.visible = buka && muka > 0;
			if (!d.visible) {
				continue;
			}
			k.xScale = k.yScale = k.zScale = SKALA_DADU;
			// turn the wanted face up: mesh faces top 1, bottom 6, front 2, back 5, right 3, left 4
			switch (muka) {
				case 6 -> k.xRot = Mth.PI;
				case 2 -> k.xRot = -Mth.HALF_PI;
				case 5 -> k.xRot = Mth.HALF_PI;
				case 3 -> k.zRot = Mth.HALF_PI;
				case 4 -> k.zRot = -Mth.HALF_PI;
				default -> {
				}
			}
			d.yRot = (s.acak * 7 + i * 131) % 360 * Mth.DEG_TO_RAD;
			if (!s.jauh && s.umur < GULING) {
				float r = 1.0F - s.umur / GULING;
				d.xRot = r * r * (4.0F + i);
				d.zRot = r * r * (3.0F - i);
				d.y -= Math.abs(Mth.sin(s.umur * 1.1F + i)) * 1.5F * r;
				d.x += (i - 1) * 0.8F * r;
			}
		}
	}
}
