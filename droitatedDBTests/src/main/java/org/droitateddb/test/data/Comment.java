/*
 * Copyright (C) 2014 The droitated DB Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.droitateddb.test.data;

import org.droitateddb.entity.AutoIncrement;
import org.droitateddb.entity.Column;
import org.droitateddb.entity.Entity;
import org.droitateddb.entity.PrimaryKey;
import org.droitateddb.entity.Relationship;

/**
 * @author Falk Appel
 * @author Alexander Frank
 */
@Entity
 public class Comment {

	 @Relationship
	 private Author author;

	 @Column
	 @PrimaryKey
	 @AutoIncrement
	 private Integer id;

	 @Column
	 private String name;

	 public Comment() {
		 // default
	 }

	 public Comment(Integer id, String name, Author author) {
		 super();
		 this.id = id;
		 this.name = name;
		 this.author = author;
	 }

	 public Comment(String name) {
		 super();
		 this.name = name;
	 }


	 public Integer getId() {
		 return this.id;
	 }

	 public String getName() {
		 return this.name;
	 }


	 public Author getAuthor() {
		 return this.author;
	 }

	 public void setAuthor(Author author) {
		 this.author = author;
	 }

	 public void setId(Integer id) {
		 this.id = id;
	 }

	 public void setName(String name) {
		 this.name = name;
	 }
 }
