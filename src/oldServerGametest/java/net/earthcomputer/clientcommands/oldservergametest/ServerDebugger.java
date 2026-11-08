package net.earthcomputer.clientcommands.oldservergametest;

import com.google.common.base.Throwables;
import com.mojang.logging.LogUtils;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ServerDebugger implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HANDLER_PROPERTY = "clientcommands:handler";

    private static final ServerMappings.MappedClass MINECRAFT_SERVER = ServerMappings.MappedClass.of("net.minecraft.server.MinecraftServer");
    private static final ServerMappings.MappedMethod TICK_SERVER = ServerMappings.MappedMethod.of(MINECRAFT_SERVER, "tickServer")
        .until(ProtocolVersion.v1_14_3, "func_71217_p");

    private final VirtualMachine vm;
    private final ServerMappings mappings;

    private ServerDebugger(VirtualMachine vm, ServerMappings mappings) {
        this.vm = vm;
        this.mappings = mappings;

        Thread eventThread = new Thread(this::handleEvents, "Server debugger events");
        eventThread.setDaemon(true);
        eventThread.start();
    }

    static ServerDebugger attach(int port, ServerMappings mappings) {
        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
            .filter(c -> c.name().equals("com.sun.jdi.SocketAttach"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No socket attaching connector"));
        Map<String, Connector.Argument> args = connector.defaultArguments();
        args.get("hostname").setValue("127.0.0.1");
        args.get("port").setValue(String.valueOf(port));
        try {
            return new ServerDebugger(connector.attach(args), mappings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (IllegalConnectorArgumentsException e) {
            throw new IllegalStateException(e);
        }
    }

    private void handleEvents() {
        try {
            while (true) {
                EventSet eventSet = vm.eventQueue().remove();
                try {
                    for (Event event : eventSet) {
                        if (event.request() != null && event.request().getProperty(HANDLER_PROPERTY) instanceof Consumer<?> handler) {
                            @SuppressWarnings("unchecked")
                            Consumer<Event> eventHandler = (Consumer<Event>) handler;
                            eventHandler.accept(event);
                        } else {
                            LOGGER.info("Unhandled debugger event {} (request={})", event, event.request());
                        }
                    }
                } catch (Throwable e) {
                    LOGGER.error("Error handling debugger event", e);
                } finally {
                    eventSet.resume();
                }
            }
        } catch (InterruptedException | VMDisconnectedException e) {
            // the server stopped or the debugger was closed
        }
    }

    /**
     * Returns a future that completes with the next instance of the given class to be constructed. This uses a
     * temporary breakpoint on the class's constructors, which is removed once it's hit.
     */
    CompletableFuture<ObjectReference> captureNextInstance(ServerMappings.MappedClass clazz) {
        String className = mappings.mapClass(clazz);
        CompletableFuture<ObjectReference> future = new CompletableFuture<>();
        EventRequestManager requestManager = vm.eventRequestManager();

        List<ReferenceType> loadedClasses = vm.classesByName(className);
        if (!loadedClasses.isEmpty()) {
            LOGGER.info("[capture {}] Class is already loaded (prepared={})", className, loadedClasses.getFirst().isPrepared());
            addConstructorBreakpoints(loadedClasses.getFirst(), future);
        } else {
            LOGGER.info("[capture {}] Class is not loaded yet, waiting for it to be prepared", className);
            ClassPrepareRequest classPrepareRequest = requestManager.createClassPrepareRequest();
            classPrepareRequest.addClassFilter(className);
            classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            classPrepareRequest.putProperty(HANDLER_PROPERTY, (Consumer<Event>) event -> {
                LOGGER.info("[capture {}] Class prepared on thread {}", className, ((ClassPrepareEvent) event).thread().name());
                requestManager.deleteEventRequest(classPrepareRequest);
                addConstructorBreakpoints(((ClassPrepareEvent) event).referenceType(), future);
            });
            classPrepareRequest.enable();
            // check for the class being prepared between checking whether it was loaded and enabling the request
            if (!vm.classesByName(className).isEmpty()) {
                LOGGER.warn("[capture {}] Class was loaded while the class prepare request was being created", className);
            }
        }

        return future;
    }

    private void addConstructorBreakpoints(ReferenceType type, CompletableFuture<ObjectReference> future) {
        EventRequestManager requestManager = vm.eventRequestManager();
        List<BreakpointRequest> breakpoints = new ArrayList<>();
        // methodsByName would also return the superclass constructors, including Object's, which are called by every thread
        for (Method constructor : type.methods().stream().filter(Method::isConstructor).toList()) {
            BreakpointRequest breakpoint = requestManager.createBreakpointRequest(constructor.location());
            breakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            breakpoint.putProperty(HANDLER_PROPERTY, (Consumer<Event>) event -> {
                LOGGER.info("[capture {}] Constructor breakpoint hit at {} on thread {}", type.name(), ((BreakpointEvent) event).location(), ((BreakpointEvent) event).thread().name());
                requestManager.deleteEventRequests(breakpoints);
                // "this" isn't valid until the superclass constructor has been called, so wait for the constructor to
                // return before getting it
                captureOnConstructorExit(((BreakpointEvent) event).thread(), type, future);
            });
            breakpoints.add(breakpoint);
        }
        breakpoints.forEach(EventRequest::enable);
        LOGGER.info("[capture {}] Enabled constructor breakpoints at {}", type.name(), breakpoints.stream().map(BreakpointRequest::location).toList());
    }

    Value getField(ObjectReference object, ServerMappings.MappedField field) {
        return object.getValue(findField(field));
    }

    void setField(ObjectReference object, ServerMappings.MappedField field, Value value) {
        try {
            object.setValue(findField(field), value);
        } catch (InvalidTypeException | ClassNotLoadedException e) {
            throw new IllegalArgumentException("Can't set " + field + " to " + value, e);
        }
    }

    void setField(ObjectReference object, ServerMappings.MappedField field, int value) {
        setField(object, field, vm.mirrorOf(value));
    }

    // Uses the declared fields of the owner class, because obfuscated field names can clash with fields in subclasses
    private Field findField(ServerMappings.MappedField mappedField) {
        String className = mappings.mapClass(mappedField.owner());
        String fieldName = mappings.mapField(mappedField);
        List<ReferenceType> types = vm.classesByName(className);
        if (types.isEmpty()) {
            throw new IllegalStateException("Class " + className + " is not loaded");
        }
        return types.getFirst().fields().stream()
            .filter(field -> field.name().equals(fieldName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No field " + className + "." + fieldName));
    }

    private Method findMethod(ServerMappings.MappedMethod mappedMethod) {
        String className = mappings.mapClass(mappedMethod.owner());
        ServerMappings.RuntimeMethod runtimeMethod = mappings.mapMethod(mappedMethod);
        List<ReferenceType> types = vm.classesByName(className);
        if (types.isEmpty()) {
            throw new IllegalStateException("Class " + className + " is not loaded");
        }
        // only the declared methods, as methodsByName also returns superclass methods, which may have the same obfuscated name
        return types.getFirst().methods().stream()
            .filter(method -> method.name().equals(runtimeMethod.name()))
            .filter(method -> runtimeMethod.descriptor() == null || method.signature().equals(runtimeMethod.descriptor()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No method " + className + "." + runtimeMethod.name() + Objects.requireNonNullElse(runtimeMethod.descriptor(), "")));
    }

    private void captureOnConstructorExit(ThreadReference thread, ReferenceType type, CompletableFuture<ObjectReference> future) {
        EventRequestManager requestManager = vm.eventRequestManager();
        MethodExitRequest methodExit = requestManager.createMethodExitRequest();
        methodExit.addThreadFilter(thread);
        methodExit.addClassFilter(type);
        methodExit.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        AtomicInteger loggedExits = new AtomicInteger();
        methodExit.putProperty(HANDLER_PROPERTY, (Consumer<Event>) event -> {
            Method method = ((MethodExitEvent) event).method();
            // don't flood the log if the constructor exit is never seen
            if (loggedExits.getAndIncrement() < 20) {
                LOGGER.info("[capture {}] Method exit of {}{}", type.name(), method.name(), method.signature());
            }
            if (!method.isConstructor()) {
                return;
            }
            requestManager.deleteEventRequest(methodExit);
            try {
                ObjectReference instance = Objects.requireNonNull(thread.frame(0).thisObject(), "got null this object in constructor");
                // don't let the reference become invalid if the object is garbage collected
                instance.disableCollection();
                LOGGER.info("[capture {}] Captured instance {}", type.name(), instance);
                future.complete(instance);
            } catch (IncompatibleThreadStateException e) {
                future.completeExceptionally(e);
            }
        });
        methodExit.enable();
        LOGGER.info("[capture {}] Enabled method exit request on thread {}", type.name(), thread.name());
    }

    /**
     * Logs the state of the debugger, to help work out why {@link #captureNextInstance} didn't capture an instance.
     */
    void logCaptureDiagnostics(ServerMappings.MappedClass clazz) {
        String className = mappings.mapClass(clazz);
        try {
            EventRequestManager requestManager = vm.eventRequestManager();
            for (ReferenceType type : vm.classesByName(className)) {
                Object instances = vm.canGetInstanceInfo() ? type.instances(10) : "(unsupported)";
                LOGGER.warn("[capture {}] Loaded type {}: prepared={}, instances={}", className, type, type.isPrepared(), instances);
            }
            LOGGER.warn("[capture {}] Class prepare requests: {}", className, requestManager.classPrepareRequests().stream().map(r -> "enabled=" + r.isEnabled()).toList());
            LOGGER.warn("[capture {}] Breakpoint requests: {}", className, requestManager.breakpointRequests().stream().map(r -> r.location() + " enabled=" + r.isEnabled()).toList());
            LOGGER.warn("[capture {}] Method exit requests: {}", className, requestManager.methodExitRequests().stream().map(r -> "enabled=" + r.isEnabled()).toList());
            for (ThreadReference thread : vm.allThreads()) {
                if (thread.name().equals("Server thread")) {
                    LOGGER.warn("[capture {}] Server thread: status={}, suspended={}, suspendCount={}", className, thread.status(), thread.isSuspended(), thread.suspendCount());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[capture {}] Failed to get diagnostics", className, e);
        }
    }

    static Value getUnmappedField(ObjectReference object, String fieldName) {
        Field field = object.referenceType().fieldByName(fieldName);
        if (field == null) {
            throw new IllegalStateException("No field " + fieldName + " in " + object.referenceType().name());
        }
        return object.getValue(field);
    }

    <T> T computeAtNextTick(Supplier<T> action) {
        EventRequestManager requestManager = vm.eventRequestManager();
        CompletableFuture<T> future = new CompletableFuture<>();
        BreakpointRequest breakpoint = requestManager.createBreakpointRequest(findMethod(TICK_SERVER).location());
        breakpoint.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        breakpoint.putProperty(HANDLER_PROPERTY, (Consumer<Event>) _ -> {
            requestManager.deleteEventRequest(breakpoint);
            if (future.isDone()) {
                return;
            }
            // the server stays suspended until the handler returns
            try {
                future.complete(action.get());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        breakpoint.enable();

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwables.throwIfUnchecked(e.getCause());
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException | TimeoutException e) {
            throw new AssertionError("Server didn't reach the next tick", e);
        } finally {
            requestManager.deleteEventRequest(breakpoint);
        }
    }

    void runAtNextTick(Runnable action) {
        computeAtNextTick(() -> {
            action.run();
            return null;
        });
    }

    @Override
    public void close() {
        try {
            vm.dispose();
        } catch (VMDisconnectedException e) {
            // already disconnected
        }
    }
}
