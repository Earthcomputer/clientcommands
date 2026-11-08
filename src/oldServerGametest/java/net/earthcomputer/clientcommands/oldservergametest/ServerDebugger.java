package net.earthcomputer.clientcommands.oldservergametest;

import com.mojang.logging.LogUtils;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
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
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ServerDebugger implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HANDLER_PROPERTY = "clientcommands:handler";

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
            addConstructorBreakpoints(loadedClasses.getFirst(), future);
        } else {
            ClassPrepareRequest classPrepareRequest = requestManager.createClassPrepareRequest();
            classPrepareRequest.addClassFilter(className);
            classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            classPrepareRequest.putProperty(HANDLER_PROPERTY, (Consumer<Event>) event -> {
                requestManager.deleteEventRequest(classPrepareRequest);
                addConstructorBreakpoints(((ClassPrepareEvent) event).referenceType(), future);
            });
            classPrepareRequest.enable();
        }

        return future;
    }

    private void addConstructorBreakpoints(ReferenceType type, CompletableFuture<ObjectReference> future) {
        EventRequestManager requestManager = vm.eventRequestManager();
        List<BreakpointRequest> breakpoints = new ArrayList<>();
        for (Method constructor : type.methodsByName("<init>")) {
            BreakpointRequest breakpoint = requestManager.createBreakpointRequest(constructor.location());
            breakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            breakpoint.putProperty(HANDLER_PROPERTY, (Consumer<Event>) event -> {
                requestManager.deleteEventRequests(breakpoints);
                // "this" isn't valid until the superclass constructor has been called, so wait for the constructor to
                // return before getting it
                captureOnConstructorExit(((BreakpointEvent) event).thread(), type, future);
            });
            breakpoints.add(breakpoint);
        }
        breakpoints.forEach(EventRequest::enable);
    }

    Value getField(ObjectReference object, ServerMappings.MappedField field) {
        return object.getValue(findField(field));
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

    private void captureOnConstructorExit(ThreadReference thread, ReferenceType type, CompletableFuture<ObjectReference> future) {
        EventRequestManager requestManager = vm.eventRequestManager();
        MethodExitRequest methodExit = requestManager.createMethodExitRequest();
        methodExit.addThreadFilter(thread);
        methodExit.addClassFilter(type);
        methodExit.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        methodExit.putProperty(HANDLER_PROPERTY, (Consumer<Event>) event -> {
            if (!((MethodExitEvent) event).method().isConstructor()) {
                return;
            }
            requestManager.deleteEventRequest(methodExit);
            try {
                ObjectReference instance = thread.frame(0).thisObject();
                // don't let the reference become invalid if the object is garbage collected
                instance.disableCollection();
                future.complete(instance);
            } catch (IncompatibleThreadStateException e) {
                future.completeExceptionally(e);
            }
        });
        methodExit.enable();
    }

    static Value getUnmappedField(ObjectReference object, String fieldName) {
        Field field = object.referenceType().fieldByName(fieldName);
        if (field == null) {
            throw new IllegalStateException("No field " + fieldName + " in " + object.referenceType().name());
        }
        return object.getValue(field);
    }

    <T> T whileSuspended(Supplier<T> action) {
        vm.suspend();
        try {
            return action.get();
        } finally {
            vm.resume();
        }
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
