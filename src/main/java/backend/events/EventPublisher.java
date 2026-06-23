package backend.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized, synchronous event publisher implementing a lightweight observer pattern
 * for internal backend event fan-out decoupling.
 *
 * <p>Consumers (e.g., ActivityLogger, MetricsAggregator, NotificationService) register
 * typed {@link BackendEventListener} instances. When an event is published, all registered
 * listeners for that event type are invoked sequentially on the calling thread. Individual
 * listener failures are isolated and logged without interrupting the remaining fan-out.</p>
 *
 * <p>This class is a singleton and thread-safe for concurrent listener registration
 * and event publishing.</p>
 */
public final class EventPublisher {

    private static final Logger LOGGER = Logger.getLogger(EventPublisher.class.getName());

    private static final EventPublisher INSTANCE = new EventPublisher();

    /**
     * Registry mapping each event class to its list of typed raw listeners.
     * ConcurrentHashMap ensures thread-safe structural modifications.
     */
    private final Map<Class<?>, List<BackendEventListener<Object>>> listenerRegistry =
            new ConcurrentHashMap<>();

    private EventPublisher() {}

    /**
     * Returns the singleton instance of the EventPublisher.
     *
     * @return The global EventPublisher instance.
     */
    public static EventPublisher getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a typed listener for a specific event class.
     *
     * <p>If the same listener instance is registered more than once for the same event
     * type, it will be invoked multiple times per publish. Callers are responsible for
     * ensuring unique registration.</p>
     *
     * @param eventClass The class of the event to subscribe to. Must not be null.
     * @param listener   The listener callback to invoke on event emission. Must not be null.
     * @param <T>        The event type parameter.
     */
    @SuppressWarnings("unchecked")
    public <T> void register(Class<T> eventClass, BackendEventListener<T> listener) {
        if (eventClass == null) {
            throw new IllegalArgumentException("eventClass must not be null.");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null.");
        }

        listenerRegistry
                .computeIfAbsent(eventClass, k -> new ArrayList<>())
                .add((BackendEventListener<Object>) listener);

        LOGGER.fine(() -> String.format(
                "Listener registered for event type [%s]: %s",
                eventClass.getSimpleName(),
                listener.getClass().getSimpleName()
        ));
    }

    /**
     * Removes a previously registered listener for a specific event class.
     *
     * @param eventClass The event class the listener was registered against.
     * @param listener   The listener instance to remove.
     * @param <T>        The event type parameter.
     */
    @SuppressWarnings("unchecked")
    public <T> void unregister(Class<T> eventClass, BackendEventListener<T> listener) {
        if (eventClass == null || listener == null) {
            return;
        }
        List<BackendEventListener<Object>> listeners = listenerRegistry.get(eventClass);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Publishes an event to all registered listeners for its runtime type.
     *
     * <p>Each listener is invoked synchronously in registration order. If a listener
     * throws an unchecked exception, the error is captured and logged, and the
     * remaining listeners continue to receive the event.</p>
     *
     * @param event The event to publish. Must not be null.
     * @param <T>   The event type parameter.
     */
    public <T> void publish(T event) {
        if (event == null) {
            throw new IllegalArgumentException("Published event must not be null.");
        }

        Class<?> eventClass = event.getClass();
        List<BackendEventListener<Object>> listeners = listenerRegistry.get(eventClass);

        if (listeners == null || listeners.isEmpty()) {
            LOGGER.fine(() -> String.format(
                    "No listeners registered for event type [%s]. Event discarded.",
                    eventClass.getSimpleName()
            ));
            return;
        }

        LOGGER.fine(() -> String.format(
                "Publishing event [%s] to %d listener(s).",
                eventClass.getSimpleName(),
                listeners.size()
        ));

        for (BackendEventListener<Object> listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format(
                        "Listener [%s] threw an exception while handling event [%s]. " +
                        "Fan-out continues for remaining listeners.",
                        listener.getClass().getSimpleName(),
                        eventClass.getSimpleName()
                ), e);
            }
        }
    }

    /**
     * Clears all registered listeners for all event types. Intended for use in
     * application teardown or test teardown scenarios only.
     */
    public void clearAllListeners() {
        listenerRegistry.clear();
        LOGGER.info("All event listeners have been cleared from EventPublisher.");
    }
}
