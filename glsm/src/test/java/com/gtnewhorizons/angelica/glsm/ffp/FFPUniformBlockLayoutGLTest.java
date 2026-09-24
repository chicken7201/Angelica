package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class FFPUniformBlockLayoutGLTest {

    /** Checks that the shared FFP block still matches the driver layout. */
    @Test
    void driverOffsetsMatchJavaConstants() {
        final VertexKey vk = VertexKey.fromState(false, false, false, false, 0);
        final FragmentKey fk = FragmentKey.fromState();
        final Program program = Program.create(vk, fk, VertexShaderGenerator.generate(vk), FragmentShaderGenerator.generate(fk), null);
        try {
            final int programId = program.getProgramId();
            final int blockIndex = GL31.glGetUniformBlockIndex(programId, FFPUniformBlock.BLOCK_NAME);
            assertNotEquals(GL31.GL_INVALID_INDEX, blockIndex, "block must be active");

            final int blockSize = GL31.glGetActiveUniformBlocki(programId, blockIndex, GL31.GL_UNIFORM_BLOCK_DATA_SIZE);
            assertEquals(FFPUniformBlock.SIZE, blockSize, "std140 block data size");

            final Object2IntMap<String> expected = FFPUniformBlock.MEMBER_OFFSETS;
            final CharSequence[] names = expected.keySet().toArray(new CharSequence[0]);
            final IntBuffer indices = BufferUtils.createIntBuffer(names.length);
            GL31.glGetUniformIndices(programId, names, indices);
            final IntBuffer offsets = BufferUtils.createIntBuffer(names.length);
            GL31.glGetActiveUniforms(programId, indices, GL31.GL_UNIFORM_OFFSET, offsets);

            for (int i = 0; i < names.length; i++) {
                final String name = names[i].toString();
                assertNotEquals(GL31.GL_INVALID_INDEX, indices.get(i), name + " must be an active block member");
                assertEquals(expected.getInt(name), offsets.get(i), "std140 offset of " + name);
            }
        } finally {
            program.destroy();
        }
    }

    /** Verifies weather values stay outside the common block and reach the weather vertex shader. */
    @Test
    void weatherUsesStandaloneUniforms() {
        assertFalse(FFPUniformBlock.GLSL_DECL.contains("u_WeatherParams"));

        final VertexKey base = VertexKey.fromState(false, false, false, false, 0);
        final VertexKey weather = VertexKey.fromPacked(VertexKey.withInstancing(base.pack(), Instancing.WEATHER));
        final String source = VertexShaderGenerator.generate(weather);
        for (int i = 0; i < 3; i++) {
            assertTrue(source.contains("uniform vec4 u_WeatherParams" + i + ";"));
        }

        final FragmentKey fragment = FragmentKey.fromState();
        final Program program = Program.create(weather, fragment, source,
            FragmentShaderGenerator.generate(fragment), null);
        final int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        try {
            final int blockIndex = GL31.glGetUniformBlockIndex(program.getProgramId(), FFPUniformBlock.BLOCK_NAME);
            assertEquals(FFPUniformBlock.SIZE,
                GL31.glGetActiveUniformBlocki(program.getProgramId(), blockIndex, GL31.GL_UNIFORM_BLOCK_DATA_SIZE));

            final int first = GL20.glGetUniformLocation(program.getProgramId(), "u_WeatherParams0");
            final int third = GL20.glGetUniformLocation(program.getProgramId(), "u_WeatherParams2");
            assertNotEquals(-1, first);
            assertNotEquals(-1, third);

            WeatherParams.set(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f);
            GL20.glUseProgram(program.getProgramId());
            program.uploadWeatherParams();

            final FloatBuffer value = BufferUtils.createFloatBuffer(4);
            GL20.glGetUniform(program.getProgramId(), first, value);
            assertEquals(1.0f, value.get(0));
            assertEquals(4.0f, value.get(3));
            GL20.glGetUniform(program.getProgramId(), third, value);
            assertEquals(9.0f, value.get(0));
            assertEquals(11.0f, value.get(2));
        } finally {
            GL20.glUseProgram(previousProgram);
            program.destroy();
        }
    }
}
