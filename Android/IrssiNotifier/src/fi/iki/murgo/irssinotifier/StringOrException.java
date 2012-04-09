package fi.iki.murgo.irssinotifier;

public class StringOrException {
	private String string;
	private Exception exception;
	
	public String getString() {
		return string;
	}
	
	public void setString(String string) {
		this.string = string;
	}
	
	public Exception getException() {
		return exception;
	}
	
	public void setException(Exception exception) {
		this.exception = exception;
	}
}
