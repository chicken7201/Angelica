package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.loading.ClassDump;
import com.gtnewhorizons.angelica.glsm.loading.ModRedirector;
import com.gtnewhorizons.angelica.glsm.testutil.MutableStatics;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.fail;

public final class GlsmRedirectAgent {

    private static final String GTNHLIB = "com/gtnewhorizon/gtnhlib/";
    private static final List<Throwable> FAILURES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger TRANSFORMED = new AtomicInteger();
    private static boolean installed;

    /** Prevents direct construction of the GlsmRedirectAgent utility. */
    private GlsmRedirectAgent() {}

    /** Installs test instrumentation before application classes are loaded. */
    public static void premain(String args, Instrumentation instrumentation) {
        install(instrumentation);
    }

    /** Installs GlsmRedirectAgent state for GlsmRedirectAgent. */
    public static void install(Instrumentation instrumentation) {
        MutableStatics.install(instrumentation);
        final ModRedirector redirector = new ModRedirector(ClassDump.DISABLED);
        addTransformer(instrumentation, (className, classfile) -> {
            if (!className.startsWith(GTNHLIB)) return null;
            final byte[] out = redirector.transform(className.replace('/', '.'), classfile, null);
            if (out == classfile) return null;
            TRANSFORMED.incrementAndGet();
            return out;
        });
        installed = true;
    }

    /** Adds transformer for GlsmRedirectAgent. */
    public static void addTransformer(Instrumentation instrumentation, BiFunction<String, byte[], byte[]> transform) {
        instrumentation.addTransformer(new Guarded(transform));
    }

    /** Rejects tests that require the redirect agent when it is absent. */
    public static void requireInstalled() {
        if (!installed) throw new IllegalStateException("GL tests need the GLSM redirect agent");
    }

    /** Checks that bytecode instrumentation completed without failures. */
    public static void assertNoFailures() {
        if (!FAILURES.isEmpty()) fail("agent transform failures: " + FAILURES, FAILURES.get(0));
    }

    /** Returns the number of classes transformed by the redirect agent. */
    public static int transformedCount() {
        return TRANSFORMED.get();
    }

    private static final class Guarded implements ClassFileTransformer {

        private final BiFunction<String, byte[], byte[]> transform;

        /** Initializes the state and dependencies used by GlsmRedirectAgent. */
        private Guarded(BiFunction<String, byte[], byte[]> transform) {
            this.transform = transform;
        }

        /** Returns the transform result for GlsmRedirectAgent. */
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain, byte[] classfile) {
            if (className == null) return null;
            try {
                return transform.apply(className, classfile);
            } catch (Throwable t) {
                FAILURES.add(new IllegalStateException("transforming " + className, t));
                return null;
            }
        }
    }
}
