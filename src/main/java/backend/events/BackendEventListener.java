package backend.events;

/**
 * Functional dispatcher interface for all internal backend domain events.
 *
 * <p>Implementing consumers register themselves with the {@link EventPublisher}
 * to receive typed event notifications synchronously. Each listener must handle
 * any internal exceptions independently to avoid interrupting the fan-out chain.</p>
 *
 * @param <T> The specific event type this listener handles.
 */
@FunctionalInterface
public interface BackendEventListener<T> {

    /**
     * Invoked synchronously by the {@link EventPublisher} when an event of type T
     * is emitted.
     *
     * @param event The published event payload. Never null.
     */
    void onEvent(T event);
}
