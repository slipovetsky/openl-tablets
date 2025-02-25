package org.openl.rules.webstudio.dependencies;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.openl.CompiledOpenClass;
import org.openl.dependency.CompiledDependency;
import org.openl.dependency.DependencyType;
import org.openl.dependency.ResolvedDependency;
import org.openl.exception.OpenLCompilationException;
import org.openl.message.OpenLErrorMessage;
import org.openl.rules.project.instantiation.AbstractDependencyManager;
import org.openl.rules.project.instantiation.DependencyLoaderInitializationException;
import org.openl.rules.project.instantiation.IDependencyLoader;
import org.openl.rules.project.model.Module;
import org.openl.rules.project.model.ProjectDescriptor;
import org.openl.types.NullOpenClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebStudioWorkspaceRelatedDependencyManager extends AbstractDependencyManager {
    private final Logger log = LoggerFactory.getLogger(WebStudioWorkspaceRelatedDependencyManager.class);

    private enum ThreadPriority {
        LOW,
        HIGH;
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final Set<ProjectDescriptor> projects;
    private final AtomicLong version = new AtomicLong(0);
    private final ThreadLocal<Long> threadVersion = new ThreadLocal<>();
    private final AtomicLong highThreadPriorityFlag = new AtomicLong(0);
    private final ThreadLocal<ThreadPriority> threadPriority = new ThreadLocal<>();
    private volatile boolean active = true;
    private volatile boolean paused = false;
    private final List<BiConsumer<IDependencyLoader, CompiledDependency>> onCompilationCompleteListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<IDependencyLoader, CompiledDependency>> onResetCompleteListeners = new CopyOnWriteArrayList<>();
    private final boolean canUnload;

    public WebStudioWorkspaceRelatedDependencyManager(Collection<ProjectDescriptor> projects,
            ClassLoader rootClassLoader,
            boolean executionMode,
            Map<String, Object> externalParameters,
            boolean canUnload) {
        super(rootClassLoader, executionMode, externalParameters);
        this.projects = new LinkedHashSet<>(Objects.requireNonNull(projects, "projects cannot be null"));
        this.canUnload = canUnload;
    }

    private DependencyType resolveDependencyType(ResolvedDependency dependency) {
        IDependencyLoader dependencyLoader = findDependencyLoader(dependency);
        return dependencyLoader != null && dependencyLoader.isProjectLoader() ? DependencyType.PROJECT
                                                                              : DependencyType.MODULE;
    }

    @Override
    public CompiledDependency loadDependency(ResolvedDependency dependency) throws OpenLCompilationException {
        ThreadPriority priority = threadPriority.get();
        if (priority == null) {
            threadPriority.set(ThreadPriority.HIGH);
        }
        if (priority == null || priority == ThreadPriority.HIGH) {
            highThreadPriorityFlag.incrementAndGet();
        }
        try {
            synchronized (this) {
                if (priority == ThreadPriority.LOW) {
                    // Low priority threads wait here
                    try {
                        while (highThreadPriorityFlag.get() > 0) {
                            this.wait();
                        }
                    } catch (InterruptedException e) {
                        throw new OpenLCompilationException("Compilation is interrupted", e);
                    }
                }
                Long currentThreadVersion = threadVersion.get();
                if (currentThreadVersion == null) {
                    threadVersion.set(version.get());
                    try {
                        log.debug("Dependency '{}' is requested with '{}' priority.",
                            dependency.getNode().getIdentifier(),
                            priority == null ? ThreadPriority.HIGH : priority);
                        if (active) {
                            return loadDependencySync(dependency);
                        } else {
                            return new CompiledDependency(dependency,
                                new CompiledOpenClass(NullOpenClass.the,
                                    Collections.singletonList(new CompilationInterruptedOpenLErrorMessage())),
                                resolveDependencyType(dependency));
                        }
                    } finally {
                        threadVersion.remove();
                    }
                } else {
                    if (active && Objects.equals(currentThreadVersion, version.get())) {
                        log.debug("Dependency '{}' is requested with '{}' priority.",
                            dependency.getNode().getIdentifier(),
                            priority == null ? ThreadPriority.HIGH : priority);
                        return loadDependencySync(dependency);
                    } else {
                        return new CompiledDependency(dependency,
                            new CompiledOpenClass(NullOpenClass.the,
                                Collections.singletonList(new CompilationInterruptedOpenLErrorMessage())),
                            resolveDependencyType(dependency));
                    }
                }
            }
        } finally {
            if (priority == null) {
                threadPriority.remove();
            }
            if (priority == null || priority == ThreadPriority.HIGH) {
                synchronized (this) {
                    highThreadPriorityFlag.decrementAndGet();
                    this.notifyAll();
                }
            }
        }
    }

    private synchronized CompiledDependency loadDependencySync(ResolvedDependency dependency) throws OpenLCompilationException {
        while (paused) {
            try {
                this.wait(50);
            } catch (InterruptedException e) {
                throw new OpenLCompilationException("Compilation is interrupted", e);
            }
        }
        return super.loadDependency(dependency);
    }

    public void pause() {
        paused = true;
        //Method should return only after all synchronized blocks have been executed
        synchronized (this){
            return;
        }
    }

    public void resume() {
        paused = false;
    }

    public ThreadLocal<Long> getThreadVersion() {
        return threadVersion;
    }

    public AtomicLong getVersion() {
        return version;
    }

    public void loadDependencyAsync(ResolvedDependency dependency, Consumer<CompiledDependency> consumer) {
        executorService.submit(() -> {
            try {
                threadPriority.set(ThreadPriority.LOW);
                CompiledDependency compiledDependency;
                try {
                    compiledDependency = this.loadDependency(dependency);
                } catch (OpenLCompilationException e) {
                    compiledDependency = new CompiledDependency(dependency,
                        new CompiledOpenClass(NullOpenClass.the, Collections.singletonList(new OpenLErrorMessage(e))),
                        resolveDependencyType(dependency));
                }
                if (compiledDependency.getCompiledOpenClass()
                    .getAllMessages()
                    .stream()
                    .anyMatch(e -> e instanceof CompilationInterruptedOpenLErrorMessage)) {
                    if (active) {
                        loadDependencyAsync(dependency, consumer);
                    }
                } else {
                    consumer.accept(compiledDependency);
                }
            } finally {
                threadPriority.remove();
            }
        });
    }

    protected Set<IDependencyLoader> initDependencyLoaders() {
        return buildDependencyLoaders(projects);
    }

    private Set<IDependencyLoader> buildDependencyLoaders(Set<ProjectDescriptor> projects) {
        Set<IDependencyLoader> dependencyLoaders = new HashSet<>();
        for (ProjectDescriptor project : projects) {
            try {
                Collection<Module> modulesOfProject = project.getModules();
                if (!modulesOfProject.isEmpty()) {
                    for (final Module m : modulesOfProject) {
                        WebStudioDependencyLoader moduleDependencyLoader = new WebStudioDependencyLoader(project,
                            m,
                            this);
                        dependencyLoaders.add(moduleDependencyLoader);
                    }
                }

                WebStudioDependencyLoader projectDependencyLoader = new WebStudioDependencyLoader(project, null, this);
                dependencyLoaders.add(projectDependencyLoader);
            } catch (Exception e) {
                throw new DependencyLoaderInitializationException(
                    String.format("Failed to initialize dependency loaders for project '%s'.", project.getName()),
                    e);
            }
        }
        return dependencyLoaders;
    }

    @Override
    public void resetOthers(ResolvedDependency... dependencies) {
        version.incrementAndGet();
        super.resetOthers(dependencies);
    }

    @Override
    public void reset(ResolvedDependency dependency) {
        version.incrementAndGet();
        super.reset(dependency);
    }

    @Override
    public void resetAll() {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    public void shutdown() {
        resume();
        synchronized (listenersMutex) {
            onCompilationCompleteListeners.clear();
            onResetCompleteListeners.clear();
        }
        active = false;
        version.incrementAndGet();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executorService.shutdownNow();
        }
        super.resetAll();
    }

    public void expand(Set<ProjectDescriptor> projects) {
        Set<IDependencyLoader> dependencyLoaders = buildDependencyLoaders(projects);
        addDependencyLoaders(dependencyLoaders);
    }

    public void registerOnCompilationCompleteListener(
            BiConsumer<IDependencyLoader, CompiledDependency> onCompilationCompleteListener) {
        onCompilationCompleteListeners.add(onCompilationCompleteListener);
    }

    public void registerOnResetCompleteListener(
            BiConsumer<IDependencyLoader, CompiledDependency> onResetCompleteListener) {
        onResetCompleteListeners.add(onResetCompleteListener);
    }

    private final Object listenersMutex = new Object();

    public void fireOnCompilationCompleteListeners(IDependencyLoader dependencyLoader,
            CompiledDependency compiledDependency) {
        synchronized (listenersMutex) {
            for (BiConsumer<IDependencyLoader, CompiledDependency> listener : onCompilationCompleteListeners) {
                try {
                    listener.accept(dependencyLoader, compiledDependency);
                } catch (Exception e) {
                    log.error("Fail during on compilation complete listener invocation.", e);
                }
            }
        }
    }

    public void fireOnResetCompleteListeners(IDependencyLoader dependencyLoader,
            CompiledDependency compiledDependency) {
        synchronized (listenersMutex) {
            for (BiConsumer<IDependencyLoader, CompiledDependency> listener : onResetCompleteListeners) {
                try {
                    listener.accept(dependencyLoader, compiledDependency);
                } catch (Exception e) {
                    log.error("Fail during on reset complete listener invocation.", e);
                }
            }
        }
    }

    public boolean isCanUnload() {
        return canUnload;
    }
}
