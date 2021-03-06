/*
 * Copyright © WebServices pour l'Éducation, 2016
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.feeder.timetable;

import org.entcore.feeder.dictionary.structures.PostImport;
import org.entcore.feeder.timetable.edt.EDTImporter;
import org.entcore.feeder.timetable.edt.EDTUtils;
import org.entcore.feeder.timetable.udt.UDTImporter;
import org.entcore.feeder.utils.ResultMessage;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImportsLauncher implements Handler<Long> {

	private static final Logger log = LoggerFactory.getLogger(ImportsLauncher.class);
	private static final Pattern UAI_PATTERN = Pattern.compile(".*([0-9]{7}[A-Z]).*");
	private final Vertx vertx;
	private final String path;
	private final PostImport postImport;
	private EDTUtils edtUtils;

	public ImportsLauncher(Vertx vertx, String path, PostImport postImport) {
		this.vertx = vertx;
		this.path = path;
		this.postImport = postImport;
	}

	public ImportsLauncher(Vertx vertx, String path, PostImport postImport, EDTUtils edtUtils) {
		this(vertx, path, postImport);
		this.edtUtils = edtUtils;
	}

	@Override
	public void handle(Long event) {
		vertx.fileSystem().readDir(path, (edtUtils != null ? ".*.xml": ".*.zip"), new Handler<AsyncResult<String[]>>() {
			@Override
			public void handle(final AsyncResult<String[]> event) {
				if (event.succeeded()) {
					final VoidHandler[] handlers = new VoidHandler[event.result().length + 1];
					handlers[handlers.length -1] = new VoidHandler() {
						@Override
						protected void handle() {
							postImport.execute();
						}
					};
					Arrays.sort(event.result());
					for (int i = event.result().length - 1; i >= 0; i--) {
						final int j = i;
						handlers[i] = new VoidHandler() {
							@Override
							protected void handle() {
								final String file = event.result()[j];
								log.info("Parsing file : " + file);
								Matcher matcher;
								if (file != null && (matcher = UAI_PATTERN.matcher(file)).find()) {

									ResultMessage m = new ResultMessage(new Handler<JsonObject>() {
										@Override
										public void handle(JsonObject event) {
											if (!"ok".equals(event.getString("status"))) {
												log.error("Error in import : " + file + " - " + event.getString("message"));
											}
											handlers[j + 1].handle(null);
										}
									})
											.put("path", file)
											.put("UAI", matcher.group(1))
											.put("language", "fr");
									if (edtUtils != null) {
										EDTImporter.launchImport(edtUtils, m);
									} else {
										UDTImporter.launchImport(vertx, m);
									}
								} else {
									log.error("UAI not found in filename : " + file);
								}
							}
						};
					}
					handlers[0].handle(null);
				} else {
					log.error("Error reading directory.");
				}
			}
		});
	}

}
