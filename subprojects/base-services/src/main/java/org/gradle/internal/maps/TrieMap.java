/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.maps;

import org.gradle.api.Action;

import java.io.File;

public class TrieMap<T> {
    public TrieMap(boolean reverseKey) {
        this.reverseKey = reverseKey;
    }

    public interface Entry<T> {
        String getKey();

        T getValue();
    }

    public interface EntryNode<T> {
        T getValue();

        void setValue(T value);
    }

    private static class Node<T> implements EntryNode<T> {
        private T value;
        private int minChar;
        private int maxChar;
        private Node[] next;

        public Node(int minChar, int maxChar) {
            this.minChar = minChar;
            this.maxChar = maxChar;
            this.next = new Node[maxChar - minChar + 1];
        }

        private Node<T> seek(char c, boolean create) {
            if (c >= minChar && c <= maxChar) {
                // within range, all good
                return seekWithinRange(c, create);
            }
            return seekOutOfRange(c, create);
        }

        private Node<T> seekOutOfRange(char c, boolean create) {
            if (c < minChar) {
                return seekLeft(c, create);
            }
            return seekRight(c, create);
        }

        private Node<T> seekLeft(char c, boolean create) {
            int diff = minChar - c;
            int length = next.length;
            Node[] shifted = new Node[length + diff];
            System.arraycopy(next, 0, shifted, diff, length);
            next = shifted;
            minChar = c;
            if (create) {
                Node<T> result = new Node<T>(minChar, maxChar);
                shifted[0] = result;
                return result;
            }
            return null;
        }

        private Node<T> seekRight(char c, boolean create) {
            int diff = c - maxChar;
            int length = next.length;
            Node[] shifted = new Node[length + diff];
            System.arraycopy(next, 0, shifted, 0, length);
            next = shifted;
            maxChar = c;
            if (create) {
                Node<T> result = new Node<T>(minChar, maxChar);
                shifted[shifted.length - 1] = result;
                return result;
            }
            return null;
        }

        private Node<T> seekWithinRange(char c, boolean create) {
            int idx = c - minChar;
            Node<T> result = next[idx];
            if (result == null && create) {
                result = new Node<T>(minChar, maxChar);
                next[idx] = result;
            }
            return result;
        }


        @Override
        public T getValue() {
            return value;
        }

        @Override
        public void setValue(T value) {
            this.value = value;
        }
    }

    private final Node<T> root = new Node<T>('a', 'z');
    private final boolean reverseKey;

    public void put(String key, T value) {
        assertValidEntry(key, value);
        Node<T> cur = fetchEntry(key);
        cur.value = value;
    }

    private Node<T> fetchEntry(String key) {
        Node<T> cur = root;
        int length = key.length();
        if (reverseKey) {
            for (int i = length - 1; i >= 0; i--) {
                cur = cur.seek(key.charAt(i), true);
            }
        } else {
            for (int i = 0; i < length; i++) {
                cur = cur.seek(key.charAt(i), true);
            }
        }
        return cur;
    }

    public T get(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        Node<T> cur = root;
        int length = key.length();
        if (reverseKey) {
            for (int i = length - 1; cur != null && i >= 0; i--) {
                cur = cur.seek(key.charAt(i), false);
            }
        } else {
            for (int i = 0; cur != null && i < length; i++) {
                cur = cur.seek(key.charAt(i), false);
            }
        }
        if (cur != null) {
            return cur.value;
        }
        return null;
    }

    /**
     * Returns a mutable view of an entry at some path. Actually creates the entry if it's missing.
     * This can be used to optimize operations that need to do something if an entry is missing.
     *
     * @param key the key of the entry
     * @return the entry
     */
    public EntryNode<T> entry(String key) {
        return fetchEntry(key);
    }

    public void forEachEntry(Action<Entry<T>> value) {
        StringBuilder sb = new StringBuilder();
        walk(root, value, sb);
    }

    protected void walk(final Node<T> cur, Action<Entry<T>> value, final StringBuilder sb) {
        if (cur.value != null) {
            value.execute(new Entry<T>() {
                @Override
                public String getKey() {
                    return reverseKey ? sb.reverse().toString() : sb.toString();
                }

                @Override
                public T getValue() {
                    return cur.value;
                }
            });
        }
        char c = (char) cur.minChar;
        for (Node node : cur.next) {
            if (node != null) {
                sb.append(c);
                walk(node, value, sb);
                sb.setLength(sb.length() - 1);
            }
            c++;
        }
    }

    private void assertValidEntry(String key, T value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
    }

    public static void main(String[] args) {
        String[] list = new File("/tmp").list();
        TrieMap<String> map = new TrieMap<String>(true);
        for (String s : list) {
            map.entry(s).setValue(s);
        }
        map.forEachEntry(new Action<Entry<String>>() {
            @Override
            public void execute(Entry<String> e) {
                System.out.println(e.getKey() + " = " + e.getValue());
            }
        });
    }

}
