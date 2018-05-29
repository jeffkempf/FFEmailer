package kempf.jeff.entities;

public class FFEmail {
    private String originalAddress;
    private String forwardedAddress;
    private long forwardedDate;
    private boolean valid;
    private String rawContent;
    private String content;
    private MessageType contentType;

    public FFEmail() {
        //default constructor
    }

    public String getOriginalAddress() {
        return originalAddress;
    }

    public void setOriginalAddress(String originalAddress) {
        this.originalAddress = originalAddress;
    }

    public String getForwardedAddress() {
        return forwardedAddress;
    }

    public void setForwardedAddress(String forwardedAddress) {
        this.forwardedAddress = forwardedAddress;
    }

    public long getForwardedDate() {
        return forwardedDate;
    }

    public void setForwardedDate(long forwardedDate) {
        this.forwardedDate = forwardedDate;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageType getContentType() {
        return contentType;
    }

    /**
     * parses keywords from email subject to determine content type
     * @param subject
     */
    public void setContentType(String subject) {
        if(subject.toLowerCase().contains("waiver")){
            //unsuccessful must come 1st since contains "successful"
            if(subject.toLowerCase().contains("unsuccessful")){
                contentType = MessageType.WAIVERFAIL;
            } else if(subject.toLowerCase().contains("successful")){
                contentType = MessageType.WAIVERSUCCESS;
            } else {
                contentType = MessageType.INVALID;
            }
        } else if(subject.toLowerCase().contains("recap")) {
            contentType = MessageType.RECAP;
        } else if(subject.toLowerCase().contains("mock draft results")){
            contentType = MessageType.MOCK;
        } else {
            contentType = MessageType.INVALID;
        }
    }

    @Override
    public String toString() {
        return "FFEmail{" +
                "originalAddress='" + originalAddress + '\'' +
                ", forwardedAddress='" + forwardedAddress + '\'' +
                ", forwardedDate=" + forwardedDate +
                ", valid=" + valid +
//                ", rawContent='" + rawContent + '\'' +
                ", content='" + content + '\'' +
                ", contentType='" + contentType + '\'' +
                '}';
    }
}
