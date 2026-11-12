package odinclient.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Environment(EnvType.CLIENT)
public final class SimpleEventBus implements EventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger("OdinClient/EventBus");
    private static final SimpleEventBus.HandlerEntry[] NO_HANDLERS =
            new SimpleEventBus.HandlerEntry[0];
    private final CopyOnWriteArrayList<SimpleEventBus.ListenerRegistration> registrations =
            new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Class<?>, SimpleEventBus.HandlerEntry[]> handlerCache =
            new ConcurrentHashMap<>();

    @Override
    public void register(Object listener) {
        if (listener != null) {
            for (SimpleEventBus.ListenerRegistration registration : this.registrations) {
                if (registration.target == listener) {
                    return;
                }
            }

            ArrayList<SimpleEventBus.HandlerEntry> entries = new ArrayList<>();

            for (Class<?> type = listener.getClass();
                    type != null && type != Object.class;
                    type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(EventHandler.class)
                            && !Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 1) {
                        try {
                            method.setAccessible(true);
                            MethodHandle handle =
                                    MethodHandles.lookup().unreflect(method).bindTo(listener);
                            boolean returnsVoid = method.getReturnType() == void.class;
                            handle =
                                    handle.asType(
                                            MethodType.methodType(
                                                    returnsVoid ? void.class : Object.class,
                                                    Object.class));

                            entries.add(
                                    new SimpleEventBus.HandlerEntry(
                                            method.getParameterTypes()[0],
                                            handle,
                                            returnsVoid,
                                            type.getName(),
                                            method.getName()));
                        } catch (IllegalAccessException | RuntimeException e) {
                            LOGGER.error(
                                    "Could not register event handler {}.{}",
                                    type.getName(),
                                    method.getName(),
                                    e);
                        }
                    }
                }
            }

            this.registrations.add(
                    new SimpleEventBus.ListenerRegistration(
                            listener, entries.toArray(NO_HANDLERS)));
            this.handlerCache.clear();
        }
    }

    @Override
    public void unregister(Object listener) {
        if (listener != null) {
            boolean removed =
                    this.registrations.removeIf(registration -> registration.target == listener);
            if (removed) {
                this.handlerCache.clear();
            }
        }
    }

    @Override
    public <T> T post(T event) {
        if (event == null) {
            return null;
        } else {
            SimpleEventBus.HandlerEntry[] handlers =
                    this.handlerCache.computeIfAbsent(event.getClass(), this::findHandlers);

            for (SimpleEventBus.HandlerEntry handler : handlers) {
                try {
                    handler.invoke(event);
                } catch (Throwable t) {
                    LOGGER.error(
                            "Event handler {}.{} failed",
                            handler.declaringClassName,
                            handler.methodName,
                            t);
                }
            }

            return event;
        }
    }

    private SimpleEventBus.HandlerEntry[] findHandlers(Class<?> eventType) {
        ArrayList<SimpleEventBus.HandlerEntry> result = new ArrayList<>();

        for (SimpleEventBus.ListenerRegistration registration : this.registrations) {
            for (SimpleEventBus.HandlerEntry handler : registration.handlers) {
                if (handler.eventType.isAssignableFrom(eventType)) {
                    result.add(handler);
                }
            }
        }

        return result.toArray(NO_HANDLERS);
    }

    private static final class HandlerEntry {
        private final Class<?> eventType;
        private final MethodHandle handle;
        private final boolean returnsVoid;
        private final String declaringClassName;
        private final String methodName;

        private HandlerEntry(
                Class<?> eventType,
                MethodHandle handle,
                boolean returnsVoid,
                String declaringClassName,
                String methodName) {
            this.eventType = eventType;
            this.handle = handle;
            this.returnsVoid = returnsVoid;
            this.declaringClassName = declaringClassName;
            this.methodName = methodName;
        }

        private void invoke(Object event) throws Throwable {
            if (this.returnsVoid) {
                this.handle.invokeExact(event);
            } else {
                Object ignored = this.handle.invokeExact(event);
            }
        }
    }

    private static final class ListenerRegistration {
        private final Object target;
        private final SimpleEventBus.HandlerEntry[] handlers;

        private ListenerRegistration(Object target, SimpleEventBus.HandlerEntry[] handlers) {
            this.target = target;
            this.handlers = handlers;
        }
    }
}
