package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;

public final class EntityIdsOnlyHandler implements ImmediateExtendedAttribHandler {

    /** Installs a test handler that requests only material ID attributes. */
    public static ImmediateExtendedAttribHandler install() {
        final ImmediateExtendedAttribHandler previous = GLSMHooks.immediateExtendedHandler;
        GLSMHooks.immediateExtendedHandler = new EntityIdsOnlyHandler();
        GLStateManager.ctx().dirtyEntityIdsAttrib = true;
        return previous;
    }

    /** Reports whether the current draw needs extended texture and tangent attributes. */
    @Override public boolean wantsExtended() { return false; }
    /** Disables tangent generation in the material-ID-only test handler. */
    @Override public boolean wantsTangent() { return false; }
    /** Reports whether the current shader consumes material ID attributes. */
    @Override public boolean wantsEntityIds() { return GLStateManager.getActiveProgram() != 0; }
    /** Disables extended attribute capture in the material-ID-only test handler. */
    @Override public boolean wantsExtendedCapture() { return false; }
    /** Disables generated face normals in the material-ID-only test handler. */
    @Override public boolean wantsFaceNormals() { return false; }
    /** Leaves vertex normals unchanged in the material-ID-only test handler. */
    @Override public void writeFaceNormals(int[] rawBuffer, int vertexCount) {}
    /** Leaves extended vertex data untouched in the material-ID-only test handler. */
    @Override public void build(int[] rawBuffer, int vertexCount, int vertsPerPrim, int normalIntIndex, long dstAddr, int dstStride) {}
    /** Leaves packed extended data untouched in the material-ID-only test handler. */
    @Override public void buildPacked(long srcBase, int stride, int posOffset, int texOffset, int normalOffset,
                                      int vertexCount, int vertsPerPrim, long dstAddr, int dstStride) {}
}
