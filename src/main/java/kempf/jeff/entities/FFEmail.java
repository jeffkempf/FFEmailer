package kempf.jeff.entities;

public class FFEmail {
    private String originalAddress;
    private String forwardedAddress;
    private long forwardedDate;
    private boolean valid;
    private String rawContent;
    private String content;
    private String contentType;

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

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        if(contentType.toLowerCase().contains("waiver")){
            if(contentType.toLowerCase().contains("successful")){
                this.contentType = "waiverSuccess";
            } else if(contentType.toLowerCase().contains("successful")){
                this.contentType = "waiverFail";
            } else {
                this.contentType = "waiverInvalid";
            }
        } else if(contentType.toLowerCase().contains("recap")) {
            this.contentType = "recap";
        } else {
            this.contentType = "unknown";
        }
    }

    @Override
    public String toString() {
        return "FFEmail{" +
                "originalAddress='" + originalAddress + '\'' +
                ", forwardedAddress='" + forwardedAddress + '\'' +
                ", forwardedDate=" + forwardedDate +
                ", valid=" + valid +
                ", rawContent='" + rawContent + '\'' +
                ", content='" + content + '\'' +
                ", contentType='" + contentType + '\'' +
                '}';
    }
}
