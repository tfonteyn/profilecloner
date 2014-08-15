package org.jboss.tfonteyne.profilecloner;

/**
 *
 * @author Tom Fonteyne
 */
public class Address
{
    public Address(String name, String value)
    {
        this.name = name;
        this.value = value;
    }

    String name;
    String value;

    public String getPath()
    {
        return "/" + name + "=" + value;
    }
}
