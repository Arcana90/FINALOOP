package backend.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventPublisher")
class EventPublisherTest {

    private EventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = EventPublisher.getInstance();
        publisher.clearAllListeners();
    }

    @AfterEach
    void tearDown() {
        publisher.clearAllListeners();
    }

    @Test
    @DisplayName("Registered listener receives published event")
    void listenerReceivesEvent() {
        List<PassSlipIssuedEvent> received = new ArrayList<>();
        publisher.register(PassSlipIssuedEvent.class, received::add);

        PassSlipIssuedEvent event = new PassSlipIssuedEvent("slip-1", "EMP-001", LocalDateTime.now());
        publisher.publish(event);

        assertEquals(1, received.size());
        assertEquals("slip-1", received.get(0).slipId());
    }

    @Test
    @DisplayName("Multiple listeners all receive the same event")
    void multipleListenersAllReceive() {
        List<PassSlipIssuedEvent> bucket1 = new ArrayList<>();
        List<PassSlipIssuedEvent> bucket2 = new ArrayList<>();

        publisher.register(PassSlipIssuedEvent.class, bucket1::add);
        publisher.register(PassSlipIssuedEvent.class, bucket2::add);

        publisher.publish(new PassSlipIssuedEvent("slip-2", "EMP-002", LocalDateTime.now()));

        assertEquals(1, bucket1.size());
        assertEquals(1, bucket2.size());
    }

    @Test
    @DisplayName("Listener for different type does not receive unrelated event")
    void listenerDoesNotReceiveWrongType() {
        List<EmployeeReturnedEvent> returnedEvents = new ArrayList<>();
        publisher.register(EmployeeReturnedEvent.class, returnedEvents::add);

        publisher.publish(new PassSlipIssuedEvent("slip-3", "EMP-003", LocalDateTime.now()));

        assertTrue(returnedEvents.isEmpty());
    }

    @Test
    @DisplayName("Failing listener does not prevent remaining listeners from receiving")
    void failingListenerDoesNotBreakFanOut() {
        List<PassSlipIssuedEvent> received = new ArrayList<>();

        // First listener throws
        publisher.register(PassSlipIssuedEvent.class, e -> {
            throw new RuntimeException("Simulated listener failure");
        });
        // Second listener must still be called
        publisher.register(PassSlipIssuedEvent.class, received::add);

        assertDoesNotThrow(() ->
            publisher.publish(new PassSlipIssuedEvent("slip-4", "EMP-004", LocalDateTime.now()))
        );
        assertEquals(1, received.size());
    }

    @Test
    @DisplayName("Unregistered listener no longer receives events")
    void unregisteredListenerDoesNotReceive() {
        List<PassSlipIssuedEvent> received = new ArrayList<>();
        BackendEventListener<PassSlipIssuedEvent> listener = received::add;

        publisher.register(PassSlipIssuedEvent.class, listener);
        publisher.unregister(PassSlipIssuedEvent.class, listener);

        publisher.publish(new PassSlipIssuedEvent("slip-5", "EMP-005", LocalDateTime.now()));

        assertTrue(received.isEmpty());
    }

    @Test
    @DisplayName("Publishing null event throws IllegalArgumentException")
    void publishNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> publisher.publish(null));
    }

    @Test
    @DisplayName("Registering null eventClass throws IllegalArgumentException")
    void registerNullClassThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> publisher.register(null, e -> {}));
    }
}
