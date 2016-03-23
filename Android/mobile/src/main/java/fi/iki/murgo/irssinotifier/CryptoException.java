
package fi.iki.murgo.irssinotifier;

public class CryptoException extends Exception {
    private static final long serialVersionUID = -4583551466500453403L;

    public CryptoException(String msg, Exception innerException) {
        super(msg, innerException);
    }
}
