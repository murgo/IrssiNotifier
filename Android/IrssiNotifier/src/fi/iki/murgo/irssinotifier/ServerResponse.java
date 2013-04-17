
package fi.iki.murgo.irssinotifier;

public class ServerResponse {
    protected boolean success;
    private String responseString;
    private Exception exception;

    public ServerResponse(Exception e) {
        exception = e;
    }

    public ServerResponse(boolean success, String responseString) {
        this.success = success;
        this.responseString = responseString;
    }

    public Exception getException() {
        return exception;
    }

    public boolean wasSuccesful() {
        return exception == null && success;
    }

    public String getResponseString() {
        return responseString;
    }

}
