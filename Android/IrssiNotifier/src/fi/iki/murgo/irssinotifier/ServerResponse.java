
package fi.iki.murgo.irssinotifier;

public class ServerResponse {
    protected boolean success;
    private String responseString;
    private Exception exception;
    private int statusCode;

    public ServerResponse(Exception e) {
        exception = e;
    }

    public ServerResponse(int statusCode, String responseString) {
        this.statusCode = statusCode;
        this.success = statusCode == 200;
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

    public int getStatusCode() {
        return statusCode;
    }

}
