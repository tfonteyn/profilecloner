/*
 *
 */
package org.jboss.tfonteyne.profilecloner;

/**
 * Used to keep a list of the elements to be cloned
 *
 * @author Tom Fonteyne
 * @author Andrea Battaglia
 */
public class Element {

    private String source;
    private String destination;

    public Element(String source) {
        this.source = source;
    }

    public Element(String from, String destination) {
        this.source = from;
        this.destination = destination;
    }

    /**
     * @return the source
     */
    public String getSource() {
        return source;
    }

    /**
     * @param source
     *            the source to set
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * @return the destination
     */
    public String getDestination() {
        return destination;
    }

    /**
     * @param destination
     *            the destination to set
     */
    public void setDestination(String destination) {
        this.destination = destination;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result)
                + ((destination == null) ? 0 : destination.hashCode());
        result = (prime * result) + ((source == null) ? 0 : source.hashCode());
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Element other = (Element) obj;
        if (destination == null) {
            if (other.destination != null) {
                return false;
            }
        } else if (!destination.equals(other.destination)) {
            return false;
        }
        if (source == null) {
            if (other.source != null) {
                return false;
            }
        } else if (!source.equals(other.source)) {
            return false;
        }
        return true;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Element [\n    source=");
        builder.append(source);
        builder.append(", \n    destination=");
        builder.append(destination);
        builder.append("\n]");
        return builder.toString();
    }

}