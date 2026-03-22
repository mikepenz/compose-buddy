package dev.mikepenz.composebuddy.renderer.android.worker

import co.touchlab.kermit.Logger

/**
 * Helper to set up LifecycleOwner and SavedStateRegistryOwner on views
 * using reflection, since these AndroidX classes are only available at runtime
 * in the forked worker process (from the project's classpath).
 *
 * Generates bytecode-equivalent classes at runtime using the project's actual
 * lifecycle/savedstate versions for compatibility.
 */
object ViewOwnerHelper {

    /**
     * Set LifecycleOwner + SavedStateRegistryOwner on a view and its parent hierarchy.
     * Returns true if successful.
     */
    fun setupViewTreeOwners(view: android.view.View, rootViewGroup: android.view.ViewGroup): Boolean {
        try {
            // 1. Create LifecycleOwner with LifecycleRegistry
            val lifecycleOwnerClass = Class.forName("androidx.lifecycle.LifecycleOwner")
            val lifecycleRegistryClass = Class.forName("androidx.lifecycle.LifecycleRegistry")

            // Use a generated class that implements LifecycleOwner
            val ownerAndRegistry = createLifecycleOwner(lifecycleOwnerClass, lifecycleRegistryClass)
            val lifecycleOwner = ownerAndRegistry.first
            val registry = ownerAndRegistry.second

            // 2. Set on view hierarchy
            setTagOnHierarchy(view, rootViewGroup, "androidx.lifecycle.runtime.R\$id", "view_tree_lifecycle_owner", lifecycleOwner)

            // 3. Create SavedStateRegistryOwner
            try {
                val ssro = createSavedStateRegistryOwner(lifecycleOwner, lifecycleOwnerClass)
                if (ssro != null) {
                    setTagOnHierarchy(view, rootViewGroup, "androidx.savedstate.R\$id", "view_tree_saved_state_registry_owner", ssro)
                }
            } catch (e: Throwable) {
                var cause: Throwable = e
                while (cause is java.lang.reflect.InvocationTargetException) cause = cause.cause ?: break
                Logger.i(cause) { "SavedState setup failed: ${cause::class.simpleName}: ${cause.message}" }
            }

            // 4. Create ViewModelStoreOwner
            try {
                setupViewModelStoreOwner(view, rootViewGroup, lifecycleOwner, lifecycleOwnerClass)
            } catch (e: Exception) {
                Logger.d { "ViewModelStore setup skipped: ${e::class.simpleName}: ${e.message}" }
            }

            // 5. Create OnBackPressedDispatcherOwner
            try {
                setupOnBackPressedDispatcherOwner(view, rootViewGroup, lifecycleOwner, lifecycleOwnerClass)
            } catch (e: Exception) {
                Logger.d { "OnBackPressedDispatcher setup skipped: ${e::class.simpleName}: ${e.message}" }
            }

            // 6. Advance lifecycle to RESUMED
            val resumed = Class.forName("androidx.lifecycle.Lifecycle\$State")
                .getDeclaredField("RESUMED").get(null)
            lifecycleRegistryClass.getDeclaredMethod("setCurrentState",
                Class.forName("androidx.lifecycle.Lifecycle\$State"))
                .invoke(registry, resumed)

            return true
        } catch (e: Exception) {
            Logger.d { "ViewTree setup: ${e::class.simpleName}: ${e.message}" }
            return false
        }
    }

    private fun setTag(view: android.view.View, rClassName: String, fieldName: String, value: Any) {
        try {
            val tagId = Class.forName(rClassName).getDeclaredField(fieldName).getInt(null)
            view.setTag(tagId, value)
        } catch (e: Exception) {
            Logger.d { "setTag($rClassName.$fieldName) failed: ${e::class.simpleName}: ${e.message}" }
        }
    }

    private fun createLifecycleOwner(
        lifecycleOwnerClass: Class<*>,
        lifecycleRegistryClass: Class<*>,
    ): Pair<Any, Any> {
        val registryHolder = arrayOfNulls<Any>(1)

        val handler = java.lang.reflect.InvocationHandler { _, method, _ ->
            if (method.name == "getLifecycle") registryHolder[0]
            else null
        }

        val unloaded = net.bytebuddy.ByteBuddy()
            .subclass(Any::class.java)
            .implement(lifecycleOwnerClass)
            .method(net.bytebuddy.matcher.ElementMatchers.named("getLifecycle"))
            .intercept(net.bytebuddy.implementation.InvocationHandlerAdapter.of(handler))
            .make()

        val loaded = unloaded.load(lifecycleOwnerClass.classLoader, net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.INJECTION)
        val ownerInstance = loaded.loaded.getDeclaredConstructor().newInstance()

        val registry = lifecycleRegistryClass
            .getConstructor(lifecycleOwnerClass)
            .newInstance(ownerInstance)
        registryHolder[0] = registry

        return Pair(ownerInstance, registry)
    }

    private fun setTagOnHierarchy(
        view: android.view.View,
        rootViewGroup: android.view.ViewGroup,
        rClassName: String,
        fieldName: String,
        value: Any,
    ) {
        setTag(view, rClassName, fieldName, value)
        setTag(rootViewGroup, rClassName, fieldName, value)
        var parent = rootViewGroup.parent as? android.view.View
        while (parent != null) {
            setTag(parent, rClassName, fieldName, value)
            parent = parent.parent as? android.view.View
        }
    }

    private fun setupOnBackPressedDispatcherOwner(
        view: android.view.View,
        rootViewGroup: android.view.ViewGroup,
        lifecycleOwner: Any,
        lifecycleOwnerClass: Class<*>,
    ) {
        val ownerClass = Class.forName("androidx.activity.OnBackPressedDispatcherOwner")
        val dispatcherClass = Class.forName("androidx.activity.OnBackPressedDispatcher")
        val dispatcher = dispatcherClass.getDeclaredConstructor().newInstance()

        val lifecycle = lifecycleOwnerClass.getMethod("getLifecycle").invoke(lifecycleOwner)

        val handler = java.lang.reflect.InvocationHandler { _, method, _ ->
            when (method.name) {
                "getOnBackPressedDispatcher" -> dispatcher
                "getLifecycle" -> lifecycle
                else -> null
            }
        }

        val unloaded = net.bytebuddy.ByteBuddy()
            .subclass(Any::class.java)
            .implement(ownerClass, lifecycleOwnerClass)
            .method(net.bytebuddy.matcher.ElementMatchers.named<net.bytebuddy.description.method.MethodDescription>("getOnBackPressedDispatcher")
                .or<net.bytebuddy.description.method.MethodDescription>(net.bytebuddy.matcher.ElementMatchers.named("getLifecycle")))
            .intercept(net.bytebuddy.implementation.InvocationHandlerAdapter.of(handler))
            .make()

        val loaded = unloaded.load(ownerClass.classLoader, net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.INJECTION)
        val owner = loaded.loaded.getDeclaredConstructor().newInstance()

        setTagOnHierarchy(view, rootViewGroup, "androidx.activity.R\$id", "view_tree_on_back_pressed_dispatcher_owner", owner)
    }

    private fun setupViewModelStoreOwner(
        view: android.view.View,
        rootViewGroup: android.view.ViewGroup,
        lifecycleOwner: Any,
        lifecycleOwnerClass: Class<*>,
    ) {
        val vmStoreOwnerClass = Class.forName("androidx.lifecycle.ViewModelStoreOwner")
        val vmStoreClass = Class.forName("androidx.lifecycle.ViewModelStore")
        val vmStore = vmStoreClass.getDeclaredConstructor().newInstance()

        val lifecycle = lifecycleOwnerClass.getMethod("getLifecycle").invoke(lifecycleOwner)

        val handler = java.lang.reflect.InvocationHandler { _, method, _ ->
            when (method.name) {
                "getViewModelStore" -> vmStore
                "getLifecycle" -> lifecycle
                else -> null
            }
        }

        // ViewModelStoreOwner may also implement HasDefaultViewModelProviderFactory — optional
        val interfaces = mutableListOf(vmStoreOwnerClass, lifecycleOwnerClass)
        try {
            interfaces.add(Class.forName("androidx.lifecycle.HasDefaultViewModelProviderFactory"))
        } catch (_: ClassNotFoundException) {}

        val unloaded = net.bytebuddy.ByteBuddy()
            .subclass(Any::class.java)
            .implement(*interfaces.toTypedArray())
            .method(net.bytebuddy.matcher.ElementMatchers.named<net.bytebuddy.description.method.MethodDescription>("getViewModelStore")
                .or<net.bytebuddy.description.method.MethodDescription>(net.bytebuddy.matcher.ElementMatchers.named("getLifecycle"))
                .or<net.bytebuddy.description.method.MethodDescription>(net.bytebuddy.matcher.ElementMatchers.named("getDefaultViewModelProviderFactory"))
                .or<net.bytebuddy.description.method.MethodDescription>(net.bytebuddy.matcher.ElementMatchers.named("getDefaultViewModelCreationExtras")))
            .intercept(net.bytebuddy.implementation.InvocationHandlerAdapter.of(handler))
            .make()

        val loaded = unloaded.load(vmStoreOwnerClass.classLoader, net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.INJECTION)
        val vmStoreOwner = loaded.loaded.getDeclaredConstructor().newInstance()

        setTagOnHierarchy(view, rootViewGroup, "androidx.lifecycle.runtime.R\$id", "view_tree_view_model_store_owner", vmStoreOwner)
    }

    private fun createSavedStateRegistryOwner(
        lifecycleOwner: Any,
        lifecycleOwnerClass: Class<*>,
    ): Any? {
        val ssroClass = Class.forName("androidx.savedstate.SavedStateRegistryOwner")
        val controllerClass = Class.forName("androidx.savedstate.SavedStateRegistryController")

        val lifecycleMethod = lifecycleOwnerClass.getMethod("getLifecycle")
        val lifecycle = lifecycleMethod.invoke(lifecycleOwner)

        val savedStateRegistryHolder = arrayOfNulls<Any>(1)
        val handler = java.lang.reflect.InvocationHandler { _, method, _ ->
            when (method.name) {
                "getLifecycle" -> lifecycle
                "getSavedStateRegistry" -> savedStateRegistryHolder[0]
                else -> null
            }
        }

        val unloaded = net.bytebuddy.ByteBuddy()
            .subclass(Any::class.java)
            .implement(ssroClass, lifecycleOwnerClass)
            .method(net.bytebuddy.matcher.ElementMatchers.named<net.bytebuddy.description.method.MethodDescription>("getLifecycle")
                .or<net.bytebuddy.description.method.MethodDescription>(net.bytebuddy.matcher.ElementMatchers.named("getSavedStateRegistry")))
            .intercept(net.bytebuddy.implementation.InvocationHandlerAdapter.of(handler))
            .make()

        val loaded = unloaded.load(ssroClass.classLoader, net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.INJECTION)
        val ssroInstance = loaded.loaded.getDeclaredConstructor().newInstance()

        val createMethod = controllerClass.getDeclaredMethod("create", ssroClass)
        val controller = createMethod.invoke(null, ssroInstance)
        // performAttach + performRestore signatures vary across savedstate versions:
        // - savedstate <1.3: performRestore(android.os.Bundle?)
        // - savedstate 1.3+: performRestore(androidx.savedstate.SavedState)
        // - some versions: create() already calls performAttach()
        try {
            controllerClass.getMethod("performAttach").invoke(controller)
        } catch (_: Throwable) {
            // Already attached or method doesn't exist
        }
        try {
            // Find performRestore by iterating declared methods — handles all savedstate versions:
            // - savedstate <1.3: performRestore(android.os.Bundle?)
            // - savedstate 1.3+: performRestore(androidx.savedstate.SavedState)
            val restoreMethod = controllerClass.declaredMethods
                .firstOrNull { it.name == "performRestore" && it.parameterCount == 1 }
            if (restoreMethod != null) {
                restoreMethod.isAccessible = true
                restoreMethod.invoke(controller, null)
            }
        } catch (_: Throwable) {
            // performRestore not available or already restored
        }
        savedStateRegistryHolder[0] = controllerClass.getMethod("getSavedStateRegistry").invoke(controller)

        return ssroInstance
    }
}
