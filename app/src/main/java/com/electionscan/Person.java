package com.electionscan;

import android.support.annotation.NonNull;

public class Person {
    @NonNull
    private String key, firstName, lastName;
    private int id, id_inside_kalpi;
    private boolean hasVoted, contacted, colored;
    private long timeOfContact;

    public Person(@NonNull String key, @NonNull String firstName, @NonNull String lastName, int id, int id_inside_kalpi,
                  boolean hasVoted, boolean contacted, long timeOfContact, boolean colored) {
        this.key = key;
        this.firstName = firstName;
        this.lastName = lastName;
        this.id = id;
        this.id_inside_kalpi = id_inside_kalpi;
        this.hasVoted = hasVoted;
        this.contacted = contacted;
        this.timeOfContact = timeOfContact;
        this.colored = colored;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @NonNull
    public String getFirstName() {
        return firstName;
    }

    @NonNull
    public String getLastName() {
        return lastName;
    }

    public int getId() {
        return id;
    }

    public int getId_inside_kalpi() {
        return id_inside_kalpi;
    }

    public boolean hasVoted() {
        return hasVoted;
    }

    public boolean isContacted() {
        return contacted;
    }

    public long getTimeOfContact() {
        return timeOfContact;
    }

    public boolean isColored() {
        return colored;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Person person = (Person) o;

        if (id != person.id) return false;
        if (id_inside_kalpi != person.id_inside_kalpi) return false;
        if (hasVoted != person.hasVoted) return false;
        if (contacted != person.contacted) return false;
        if (colored != person.colored) return false;
        if (timeOfContact != person.timeOfContact) return false;
        if (!key.equals(person.key)) return false;
        if (!firstName.equals(person.firstName)) return false;
        return lastName.equals(person.lastName);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + firstName.hashCode();
        result = 31 * result + lastName.hashCode();
        result = 31 * result + id;
        result = 31 * result + id_inside_kalpi;
        result = 31 * result + (hasVoted ? 1 : 0);
        result = 31 * result + (contacted ? 1 : 0);
        result = 31 * result + (colored ? 1 : 0);
        result = 31 * result + (int) (timeOfContact ^ (timeOfContact >>> 32));
        return result;
    }
}
