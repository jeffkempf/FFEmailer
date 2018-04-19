package kempf.jeff.entities;

import java.util.Properties;

public class EmailThreshold {
    private String address;
    private long firstTstamp;
    private long cutoffTstamp;
    private int count;
    private int cutoffCount;

    public EmailThreshold (String address, long firstTstamp, Properties properties) {
        this.address = address;
        this.firstTstamp = firstTstamp;
        count = 1; //might change to 0 depending on implementation
        cutoffTstamp = firstTstamp + Long.parseLong(properties.getProperty("email.threshold.timer"));
        cutoffCount = Integer.parseInt(properties.getProperty("email.threshold"));
    }

    public void increment() {
        count++;
    }

    /**
     * scenarios to parse:
     * within cutoff count and tstamp
     * within cutoff count only
     * not within either (get confirmation on this)
     *
     * scenarios not to parse:
     * within cutoff tstamp only
     *
     * @return true means ignore
     */
    public boolean shouldIgnore() {
        return count >= cutoffCount;
    }

	/*public boolean shouldIgnore(int size) {
		if(count >= cutoffCount && size > cutoffSize) {
			return true;
		}
		return false;
		//return count >= cutoffCount;
	}*/

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public long getFirstTstamp() {
        return firstTstamp;
    }

    public void setFirstTstamp(long firstTstamp) {
        this.firstTstamp = firstTstamp;
    }

    public long getCutoffTstamp() {
        return cutoffTstamp;
    }

    public void setCutoffTstamp(long cutoffTstamp) {
        this.cutoffTstamp = cutoffTstamp;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getCutoffCount() {
        return cutoffCount;
    }

    @Override
    public String toString() {
        return "EmailThreshold [address=" + address + ", firstTstamp=" + firstTstamp + ", cutoffTstamp=" + cutoffTstamp
                + ", count=" + count + ", cutoffCount=" + cutoffCount + "]";
    }


}
