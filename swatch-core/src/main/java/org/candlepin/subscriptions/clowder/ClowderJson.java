/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.clowder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Class to represent the clowder JSON for the ClowderJsonPathPropertySource */
public class ClowderJson {
  public static final String EMPTY_JSON = "{}";

  private final JsonNode root;

  public ClowderJson() throws IOException {
    InputStream s = new ByteArrayInputStream(EMPTY_JSON.getBytes(StandardCharsets.UTF_8));
    ObjectMapper mapper = new ObjectMapper();
    this.root = mapper.readTree(s);
  }

  public ClowderJson(InputStream s, ObjectMapper mapper) throws IOException {
    this.root = mapper.readTree(s);
  }

  public JsonNode getRoot() {
    return root;
  }
}
