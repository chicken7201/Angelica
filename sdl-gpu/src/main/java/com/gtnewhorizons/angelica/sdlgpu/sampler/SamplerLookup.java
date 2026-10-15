package com.gtnewhorizons.angelica.sdlgpu.sampler;

import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.List;

public final class SamplerLookup {
    private static final int[] EMPTY_UNITS = new int[0];
    private SamplerLookup() {}

    /** Returns the cached texture units for ordinary samplers in one stage. */
    public static int[] resolvedUnits(ShaderManager.ProgramObject prog, boolean fragment) {
        refresh(prog);
        return fragment ? prog.fragmentSamplerUnits : prog.vertexSamplerUnits;
    }

    /** Returns the cached texture units for moved samplers in one stage. */
    public static int[] movedSamplerUnits(ShaderManager.ProgramObject prog, boolean fragment) {
        refresh(prog);
        return fragment ? prog.fragmentMovedSamplerUnits : prog.vertexMovedSamplerUnits;
    }

    /** Refreshes cached sampler units when program assignments change. */
    private static void refresh(ShaderManager.ProgramObject prog) {
        if (!prog.samplerUnitsDirty) return;
        prog.vertexSamplerUnits = resolveStage(prog.vertexSamplerNames, prog.samplerTextureUnits, prog.vertexResources.numSamplers());
        prog.fragmentSamplerUnits = resolveStage(prog.fragmentSamplerNames, prog.samplerTextureUnits, prog.fragmentResources.numSamplers());
        prog.vertexMovedSamplerUnits = resolveMoved(prog.vertexImageNames, prog.vertexMovedSamplerRead, prog.samplerTextureUnits);
        prog.fragmentMovedSamplerUnits = resolveMoved(prog.fragmentImageNames, prog.fragmentMovedSamplerRead, prog.samplerTextureUnits);
        prog.samplerUnitsDirty = false;
        prog.samplerUnitsGeneration++;
    }

    /** Resolves sampler bindings using explicit assignments and unit-zero defaults. */
    private static int[] resolveStage(List<String> samplerNames, Object2IntMap<String> textureUnits, int samplerCount) {
        if (samplerCount <= 0) return EMPTY_UNITS;
        final int[] units = new int[samplerCount];
        for (int i = 0; i < samplerCount; i++) {
            units[i] = getSamplerTextureUnit(samplerNames, textureUnits, i);
        }
        return units;
    }

    /** Resolves texture units only for moved samplers read by the shader. */
    private static int[] resolveMoved(List<String> imageNames, boolean[] read, Object2IntMap<String> textureUnits) {
        if (read.length == 0) return EMPTY_UNITS;
        final int[] units = new int[read.length];
        for (int i = 0; i < units.length; i++) {
            units[i] = read[i] ? getSamplerTextureUnit(imageNames, textureUnits, i) : -1;
        }
        return units;
    }

    /** Resolves one sampler binding with the GL unit-zero default. */
    public static int getSamplerTextureUnit(List<String> samplerNames, Object2IntMap<String> textureUnits, int bindingIndex) {
        if (bindingIndex >= samplerNames.size()) return 0;
        final String name = samplerNames.get(bindingIndex);
        final int unit = textureUnits.getInt(name);
        return unit != -1 ? unit : 0;
    }
}
