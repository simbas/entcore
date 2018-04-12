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

package org.entcore.directory.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.Either;
import io.vertx.core.eventbus.DeliveryOptions;
import org.entcore.common.mongodb.MongoDbResult;
import org.entcore.directory.Directory;
import org.entcore.directory.pojo.ImportInfos;
import org.entcore.directory.services.ImportService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;
import static fr.wseduc.webutils.Utils.defaultValidationParamsNull;
import static fr.wseduc.webutils.Utils.isNotEmpty;

public class DefaultImportService implements ImportService {

	private static final Logger log = LoggerFactory.getLogger(DefaultImportService.class);
	private static final long TIMEOUT = 10 * 60 * 1000l;
	private final EventBus eb;
	private final Vertx vertx;
	private static final ObjectMapper mapper = new ObjectMapper();
	private final MongoDb mongo = MongoDb.getInstance();
	private static final String IMPORTS = "imports";

	public DefaultImportService(Vertx vertx, EventBus eb) {
		this.eb = eb;
		this.vertx = vertx;
	}

	@Override
	public void validate(ImportInfos importInfos, final Handler<Either<JsonObject, JsonObject>> handler) {
		try {
			JsonObject action = new JsonObject(mapper.writeValueAsString(importInfos))
					.put("action", "validate");
			eb.send(Directory.FEEDER, action, new DeliveryOptions().setSendTimeout(TIMEOUT), handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> res) {
					if ("ok".equals(res.body().getString("status"))) {
						JsonObject r = res.body().getJsonObject("result", new JsonObject());
						if (r.getJsonObject("errors", new JsonObject()).size() > 0) {
							handler.handle(new Either.Left<JsonObject, JsonObject>(r.getJsonObject("errors")));
						} else {
							JsonObject f = r.getJsonObject("files");
							if(r.getJsonObject("softErrors") != null) {
								f.put("softErrors", r.getJsonObject("softErrors"));
							}
							if (isNotEmpty(r.getString("_id"))) {
								f.putString("importId", r.getString("_id"));
							}
							handler.handle(new Either.Right<JsonObject, JsonObject>(f));
						}
					} else {
						handler.handle(new Either.Left<JsonObject, JsonObject>(
								new JsonObject().put("global",
								new fr.wseduc.webutils.collections.JsonArray().add(res.body().getString("message", "")))));
					}
				}
			}));
		} catch (JsonProcessingException e) {
			handler.handle(new Either.Left<JsonObject, JsonObject>(new JsonObject()
					.put("global", new fr.wseduc.webutils.collections.JsonArray().add("unexpected.error"))));
			log.error(e.getMessage(), e);
		}
	}

	@Override
	public void validate(String importId, final Handler<Either<JsonObject, JsonObject>> handler) {
		JsonObject action = new JsonObject().putString("action", "validateWithId").putString("id", importId);
		sendCommand(handler, action);
	}

	@Override
	public void doImport(ImportInfos importInfos, final Handler<Either<JsonObject, JsonObject>> handler) {
		try {
			JsonObject action = new JsonObject(mapper.writeValueAsString(importInfos))
					.put("action", "import");
			eb.send("entcore.feeder", action, new DeliveryOptions().setSendTimeout(TIMEOUT), handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> event) {
					if ("ok".equals(event.body().getString("status"))) {
						JsonObject r = event.body().getJsonObject("result", new JsonObject());
						if (r.getJsonObject("errors", new JsonObject()).size() > 0) {
							handler.handle(new Either.Left<JsonObject, JsonObject>(r.getJsonObject("errors")));
						} else {
							handler.handle(new Either.Right<JsonObject, JsonObject>(r.getJsonObject("ignored")));
						}
					} else {
						handler.handle(new Either.Left<JsonObject, JsonObject>(new JsonObject().put("global",
								new fr.wseduc.webutils.collections.JsonArray().add(event.body().getString("message", "")))));
					}
			}
			}));
		} catch (JsonProcessingException e) {
			handler.handle(new Either.Left<JsonObject, JsonObject>(new JsonObject()
					.put("global", new fr.wseduc.webutils.collections.JsonArray().add("unexpected.error"))));
			log.error(e.getMessage(), e);
		}
	}

	@Override
	public void doImport(String importId, final Handler<Either<JsonObject, JsonObject>> handler) {
		JsonObject action = new JsonObject().putString("action", "importWithId").putString("id", importId);
		sendCommand(handler, action);
	}

	@Override
	public void columnsMapping(ImportInfos importInfos, final Handler<Either<JsonObject, JsonObject>> handler) {
		try {
			JsonObject action = new JsonObject(mapper.writeValueAsString(importInfos))
					.putString("action", "columnsMapping");
			sendCommand(handler, action);
		} catch (JsonProcessingException e) {
			handler.handle(new Either.Left<JsonObject, JsonObject>(new JsonObject()
					.putArray("global", new JsonArray().addString("unexpected.error"))));
			log.error(e.getMessage(), e);
		}
	}

	@Override
	public void classesMapping(ImportInfos importInfos, final Handler<Either<JsonObject, JsonObject>> handler) {
		try {
			JsonObject action = new JsonObject(mapper.writeValueAsString(importInfos))
					.putString("action", "classesMapping");
			sendCommand(handler, action);
		} catch (JsonProcessingException e) {
			handler.handle(new Either.Left<JsonObject, JsonObject>(new JsonObject()
					.putArray("global", new JsonArray().addString("unexpected.error"))));
			log.error(e.getMessage(), e);
		}
	}

	@Override
	public void addLine(String importId, String profile, JsonObject line, Handler<Either<String, JsonObject>> handler) {
		final JsonObject query = new JsonObject().putString("_id", importId);
		final JsonObject update = new JsonObject().putObject("$push", new JsonObject().putObject("files." + profile, line));
		mongo.update(IMPORTS, query, update, MongoDbResult.validActionResultHandler(handler));
	}

	@Override
	public void updateLine(String importId, String profile, JsonObject line, Handler<Either<String, JsonObject>> handler) {
		Integer lineId = line.getInteger("line");
		if (defaultValidationParamsNull(handler, lineId)) return;
		JsonObject item = new JsonObject();
		for (String attr : line.getFieldNames()) {
			if ("line".equals(attr)) continue;
			item.putValue("files." + profile + ".$." + attr, line.getValue(attr));
		}
//		db.imports.update({"_id" : "8ff9a53f-a216-49f2-97cf-7ccc41c6e2b6", "files.Relative.line" : 147}, {$set : {"files.Relative.$.state" : "bla"}})
		final JsonObject query = new JsonObject().putString("_id", importId).putNumber("files." + profile + ".line", lineId);
		final JsonObject update = new JsonObject().putObject("$set", item);
		mongo.update(IMPORTS, query, update, MongoDbResult.validActionResultHandler(handler));
	}

	@Override
	public void deleteLine(String importId, String profile, Integer line, Handler<Either<String, JsonObject>> handler) {
		final JsonObject query = new JsonObject().putString("_id", importId).putNumber("files." + profile + ".line", line);
		final JsonObject update = new JsonObject().putObject("$pull", new JsonObject()
				.putObject("files." + profile, new JsonObject().putNumber("line", line)));
		mongo.update(IMPORTS, query, update, MongoDbResult.validActionResultHandler(handler));
	}

	protected void sendCommand(final Handler<Either<JsonObject, JsonObject>> handler, JsonObject action) {
		eb.send("entcore.feeder", action, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				if ("ok".equals(event.body().getString("status"))) {
					JsonObject r = event.body().getObject("result", new JsonObject());
					r.removeField("status");
					if (r.getObject("errors", new JsonObject()).size() > 0) {
						handler.handle(new Either.Left<JsonObject, JsonObject>(r));
					} else {
						handler.handle(new Either.Right<JsonObject, JsonObject>(r));
					}
				} else {
					handler.handle(new Either.Left<JsonObject, JsonObject>(new JsonObject().putArray("global",
							new JsonArray().addString(event.body().getString("message", "")))));
				}
			}
		});
	}

}
