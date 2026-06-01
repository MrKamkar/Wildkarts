package com.wildkarts.net;

/**
 * Pakiet potwierdzenia — wysyłany w odpowiedzi na {@link ReliablePacket}.
 * Zawiera {@code sequenceId} potwierdzanego pakietu.
 *
 * <p>Nie rozszerza {@link NetPacket} — to lekka wiadomość kontrolna bez własnej sekwencji.</p>
 */
public class AckPacket {

    /** Identyfikator sekwencji potwierdzanego {@link ReliablePacket}. */
    public long acknowledgedId;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public AckPacket() {
    }

    /**
     * Tworzy potwierdzenie dla podanego identyfikatora sekwencji.
     *
     * @param acknowledgedId identyfikator potwierdzanego pakietu
     */
    public AckPacket(long acknowledgedId) {
        this.acknowledgedId = acknowledgedId;
    }
}
