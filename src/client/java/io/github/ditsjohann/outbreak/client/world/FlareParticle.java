package io.github.ditsjohann.outbreak.client.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A distress flare a long way off: it shoots up, bursts, and hangs burning red as it drifts down,
 * flickering. Bright whatever the light (it's its own light), and big, as it's drawn far away.
 */
public class FlareParticle extends SingleQuadParticle {
	private static final int RISE = 30;

	private final float size;

	private FlareParticle(ClientLevel level, double x, double y, double z, double rise, SpriteSet sprites) {
		super(level, x, y, z, sprites.first());
		this.lifetime = 20 * 28;
		this.size = 4.0F;
		this.quadSize = this.size * 0.4F;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		this.xd = 0.0;
		this.yd = rise / RISE;
		this.zd = 0.0;
		this.setColor(1.0F, 0.35F, 0.25F);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.age < RISE) {
			this.quadSize = this.size * (0.4F + 0.3F * this.age / RISE);
		} else {
			// Burst, then a slow fall on its parachute, flickering.
			this.yd = -0.02;
			this.quadSize = this.size * (0.85F + this.random.nextFloat() * 0.3F);
			this.setColor(1.0F, 0.25F + this.random.nextFloat() * 0.25F, 0.2F);
			if (this.age > this.lifetime - 60) {
				this.setAlpha((this.lifetime - this.age) / 60.0F);
			}
		}
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	public int getLightCoords(float partialTicks) {
		return 15728880;
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
			double xAux, double yAux, double zAux, RandomSource random) {
			return new FlareParticle(level, x, y, z, yAux, this.sprites);
		}
	}
}
