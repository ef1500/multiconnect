package net.earthcomputer.multiconnect.mixin.bridge;

import net.earthcomputer.multiconnect.impl.Utils;
import net.earthcomputer.multiconnect.protocols.generic.IParticleManager;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Mixin(ParticleManager.class)
public class MixinParticleManager implements IParticleManager {

    @Shadow @Final private Map<Identifier, Object> spriteAwareFactories;
    @Shadow protected ClientWorld world;

    @Unique private Map<Identifier, ParticleFactory<?>> customFactories = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private <T extends ParticleEffect> void onCreateParticle(T effect, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> ci) {
        ParticleFactory<T> customFactory = (ParticleFactory<T>) customFactories.get(Registry.PARTICLE_TYPE.getId(effect.getType()));
        if (customFactory != null)
            ci.setReturnValue(customFactory.createParticle(effect, world, x, y, z, xSpeed, ySpeed, zSpeed));
    }

    @Redirect(method = "createParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/registry/Registry;getRawId(Ljava/lang/Object;)I"))
    private int redirectRawId(Registry<ParticleType<?>> registry, Object type) {
        return Utils.getUnmodifiedId(registry, (ParticleType<?>) type);
    }

    @ModifyVariable(method = "loadTextureList", ordinal = 0, at = @At("HEAD"))
    private Identifier modifyIdentifier(Identifier id) {
        Identifier unmodifiedName = Utils.getUnmodifiedName(Registry.PARTICLE_TYPE, Registry.PARTICLE_TYPE.get(id));
        return unmodifiedName == null ? id : unmodifiedName;
    }

    @Override
    public <T extends ParticleEffect> void multiconnect_registerFactory(ParticleType<T> type, ParticleFactory<T> factory) {
        customFactories.put(Registry.PARTICLE_TYPE.getId(type), factory);
    }

    @Override
    public <T extends ParticleEffect> void multiconnect_registerSpriteAwareFactory(ParticleType<T> type,
                                                                                   Function<SpriteProvider, ParticleFactory<T>> spriteAwareFactory) {
        SpriteProvider spriteProvider = ((ParticleManager)(Object)this).new SimpleSpriteProvider();

        Identifier id = Registry.PARTICLE_TYPE.getId(type);
        spriteAwareFactories.put(id, spriteProvider);
        customFactories.put(id, spriteAwareFactory.apply(spriteProvider));
    }
}
