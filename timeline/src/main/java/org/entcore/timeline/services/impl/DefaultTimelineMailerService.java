/*
 * Copyright © WebServices pour l'Éducation, 2017
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

package org.entcore.timeline.services.impl;

import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Server;
import fr.wseduc.webutils.email.EmailSender;
import fr.wseduc.webutils.http.Renders;
import org.entcore.common.email.EmailFactory;
import org.entcore.common.http.request.JsonHttpServerRequest;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;
import org.entcore.common.notification.TimelineNotificationsLoader;
import org.entcore.timeline.controllers.TimelineLambda;
import org.entcore.timeline.services.TimelineConfigService;
import org.entcore.timeline.services.TimelineMailerService;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Container;

import java.io.StringReader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultTimelineMailerService extends Renders implements TimelineMailerService {

	private static final Logger log = LoggerFactory.getLogger(DefaultTimelineMailerService.class);
	private static final String USERBOOK_ADDRESS = "userbook.preferences";
	private final EventBus eb;
	private Map<String, String> registeredNotifications;
	private TimelineConfigService configService;
	private ConcurrentMap<String, String> eventsI18n;
	private HashMap<String, JsonObject> lazyEventsI18n;
	private final EmailSender emailSender;
	private final int USERS_LIMIT;
	private final MongoDb mongo = MongoDb.getInstance();
	private final Neo4j neo4j = Neo4j.getInstance();

	public DefaultTimelineMailerService(Vertx vertx, Container container) {
		super(vertx, container);
		eb = Server.getEventBus(vertx);
		EmailFactory emailFactory = new EmailFactory(this.vertx, container, container.config());
		emailSender = emailFactory.getSender();
		USERS_LIMIT = container.config().getInteger("users-loop-limit", 25);
	}

	/* Override i18n to use additional timeline translations and nested templates */
	@Override
	protected void setLambdaTemplateRequest(final HttpServerRequest request, final Map<String, Object> ctx) {
		super.setLambdaTemplateRequest(request, ctx);
		TimelineLambda.setLambdaTemplateRequest(request, ctx, eventsI18n, lazyEventsI18n);
	}

	@Override
	public void sendImmediateMails(final HttpServerRequest request, final String notificationName, final JsonObject notification,
								   final JsonObject templateParameters, final JsonArray recipientIds){
		//Get notification properties (mixin : admin console configuration which overrides default properties)
		getNotificationProperties(notificationName, new Handler<Either<String, JsonObject>>() {
			public void handle(final Either<String, JsonObject> properties) {
				if(properties.isLeft() || properties.right().getValue() == null){
					log.error("[sendImmediateMails] Issue while retrieving notification (" + notificationName + ") properties.");
					return;
				}
				//Get users preferences (overrides notification properties)
				getUsersPreferences(recipientIds, new Handler<JsonArray>() {
					public void handle(final JsonArray userList) {
						if(userList == null){
							log.error("[sendImmediateMails] Issue while retrieving users preferences.");
							return;
						}
						//Process template once by domain
						final Map<String, Map<String, String>> processedTemplates = new HashMap<>();
						templateParameters.putString("innerTemplate", notification.getString("template", ""));

						final Map<String, Map<String, List<Object>>> toByDomainLang = new HashMap<>();

						final AtomicInteger userCount = new AtomicInteger(userList.size());
						final VoidHandler templatesHandler = new VoidHandler(){
							protected void handle() {
								if(userCount.decrementAndGet() == 0){
									if(toByDomainLang.size() > 0){
										//On completion : log
										final Handler<Message<JsonObject>> completionHandler = new Handler<Message<JsonObject>>(){
											public void handle(Message<JsonObject> event) {
												if("error".equals(event.body().getString("status", "error"))){
													log.error("[Timeline immediate emails] Error while sending mails : " + event.body());
												} else {
													log.debug("[Timeline immediate emails] Immediate mails sent.");
												}
											}
										};

										JsonArray keys = new JsonArray()
												.add("timeline.immediate.mail.subject.header")
												.add(notificationName.toLowerCase());

										for(final String domain : toByDomainLang.keySet()){
											for(final String lang : toByDomainLang.get(domain).keySet()){
												translateTimeline(keys, domain, lang, new Handler<JsonArray>() {
													public void handle(JsonArray translations) {
														//Send mail containing the "immediate" notification
														emailSender.sendEmail(request,
																toByDomainLang.get(domain).get(lang),
																null,
																null,
																translations.get(0).toString() + translations.get(1).toString(),
																processedTemplates.get(domain).get(lang),
																null,
																false,
																completionHandler);
													}
												});
											}
										}
									}
								}
							}
						};

						for(Object userObj : userList){
							final JsonObject userPref = ((JsonObject) userObj);
							final String userDomain = userPref.getString("lastDomain", I18n.DEFAULT_DOMAIN);
							final String userScheme = userPref.getString("lastScheme", "http");
							String mutableLanguage = "fr";
							try {
								mutableLanguage = new JsonObject(userPref.getString("language", "{}")).getString("default-domain", "fr");
							} catch(Exception e) {
								log.error("UserId [" + userPref.getString("userId", "") + "] - Bad language preferences format");
							}
							final String userLanguage = mutableLanguage;

							if(!processedTemplates.containsKey(userDomain))
								processedTemplates.put(userDomain, new HashMap<String, String>());
							JsonObject notificationPreference = userPref
									.getObject("preferences", new JsonObject())
									.getObject("config", new JsonObject())
									.getObject(notificationName, new JsonObject());
							// If the frequency is IMMEDIATE
							// and the restriction is not INTERNAL (timeline only)
							// and if the user has provided an email
							if(TimelineNotificationsLoader.Frequencies.IMMEDIATE.name().equals(
									notificationPreference.getString("defaultFrequency", properties.right().getValue().getString("defaultFrequency"))) &&
									!TimelineNotificationsLoader.Restrictions.INTERNAL.name().equals(
											notificationPreference.getString("restriction", properties.right().getValue().getString("restriction"))) &&
									!TimelineNotificationsLoader.Restrictions.HIDDEN.name().equals(
											notificationPreference.getString("restriction", properties.right().getValue().getString("restriction"))) &&
									userPref.getString("userMail") != null && !userPref.getString("userMail").trim().isEmpty()){
								if(!toByDomainLang.containsKey(userDomain)){
									toByDomainLang.put(userDomain,new HashMap<String, List<Object>>());
								}
								if(!toByDomainLang.get(userDomain).containsKey(userLanguage)){
									toByDomainLang.get(userDomain).put(userLanguage, new ArrayList<Object>());
								}
								toByDomainLang.get(userDomain).get(userLanguage).add(userPref.getString("userMail"));
							}
							if(!processedTemplates.get(userDomain).containsKey(userLanguage)){
								processTimelineTemplate(templateParameters, "", "notifications/immediate-mail.html",
										userDomain, userScheme, userLanguage, false, new Handler<String>(){
											public void handle(String processedTemplate) {
												processedTemplates.get(userDomain).put(userLanguage, processedTemplate);
												templatesHandler.handle(null);
											}
										});
							} else {
								templatesHandler.handle(null);
							}
						}
					}
				});
			}
		});
	}

	@Override
	public void getNotificationProperties(final String notificationKey, final Handler<Either<String, JsonObject>> handler) {
		configService.list(new Handler<Either<String, JsonArray>>() {
			public void handle(Either<String, JsonArray> event) {
				if (event.isLeft()) {
					handler.handle(new Either.Left<String, JsonObject>(
							event.left().getValue()));
					return;
				}
				final String notificationStr = registeredNotifications
						.get(notificationKey.toLowerCase());
				if (notificationStr == null) {
					handler.handle(new Either.Left<String, JsonObject>(
							"invalid.notification.key"));
					return;
				}
				final JsonObject notification = new JsonObject(notificationStr);
				for (Object notifConfigObj : event.right().getValue()) {
					JsonObject notifConfig = (JsonObject) notifConfigObj;
					if (notifConfig.getString("key", "")
							.equals(notificationKey.toLowerCase())) {
						notification.putString("defaultFrequency",
								notifConfig.getString("defaultFrequency", ""));
						notification.putString("restriction",
								notifConfig.getString("restriction", ""));
						break;
					}
				}
				handler.handle(
						new Either.Right<String, JsonObject>(notification));
			}
		});
	}

	@Override
	public void translateTimeline(JsonArray i18nKeys, String domain, String language, Handler<JsonArray> handler) {
		String i18n = eventsI18n.get(language.split(",")[0].split("-")[0]);
		final JsonObject timelineI18n;
		if (i18n == null) {
			timelineI18n = new JsonObject();
		} else {
			timelineI18n = new JsonObject("{" + i18n.substring(0, i18n.length() - 1) + "}");
		}
		timelineI18n.mergeIn(I18n.getInstance().load(language, domain));
		JsonArray translations = new JsonArray();
		for(Object keyObj : i18nKeys){
			String key = (String) keyObj;
			translations.add(timelineI18n.getString(key, key));
		}
		handler.handle(translations);
	}

	@Override
	public void processTimelineTemplate(JsonObject parameters, String resourceName,
			String template, String domain, String scheme, String language, boolean reader, final Handler<String> handler) {
		final HttpServerRequest request = new JsonHttpServerRequest(new JsonObject()
				.putObject("headers", new JsonObject()
						.putString("Host", domain)
						.putString("X-Forwarded-Proto", scheme)
						.putString("Accept-Language", language)));
		if(reader){
			final StringReader templateReader = new StringReader(template);
			processTemplate(request, parameters, resourceName, templateReader, new Handler<Writer>() {
				public void handle(Writer writer) {
					handler.handle(writer.toString());
				}
			});

		} else {
			processTemplate(request, template, parameters, handler);
		}
	}


	@Override
	public void sendDailyMails(int dayDelta, final Handler<Either<String, JsonObject>> handler){

		final HttpServerRequest request = new JsonHttpServerRequest(new JsonObject());
		final AtomicInteger userPagination = new AtomicInteger(0);
		final AtomicInteger endPage = new AtomicInteger(0);
		final Calendar dayDate = Calendar.getInstance();
		dayDate.add(Calendar.DAY_OF_MONTH, dayDelta);
		dayDate.set(Calendar.HOUR_OF_DAY, 0);
		dayDate.set(Calendar.MINUTE, 0);
		dayDate.set(Calendar.SECOND, 0);
		dayDate.set(Calendar.MILLISECOND, 0);

		final JsonObject results = new JsonObject()
				.putNumber("mails.sent", 0)
				.putNumber("users.ko", 0);
		final JsonObject notificationsDefaults = new JsonObject();
		final List<String> notifiedUsers = new ArrayList<>();

		final Handler<Boolean> userContinuationHandler = new Handler<Boolean>() {

			private final Handler<Boolean> continuation = this;
			private final Handler<JsonArray> usersHandler = new Handler<JsonArray>() {
				public void handle(final JsonArray users) {
					final int nbUsers = users.size();
					if(nbUsers == 0){
						log.info("[DailyMails] Page0 : " + userPagination.get() + "/" + endPage.get());
						continuation.handle(userPagination.get() != endPage.get());
						return;
					}
					final AtomicInteger usersCountdown = new AtomicInteger(nbUsers);

					final VoidHandler usersEndHandler = new VoidHandler() {
						protected void handle() {
							if(usersCountdown.decrementAndGet() <= 0){
								log.info("[DailyMails] Page : " + userPagination.get() + "/" + endPage.get());
								continuation.handle(userPagination.get() != endPage.get());
							}
						}
					};

					final JsonArray userIds = new JsonArray();
					for(Object userObj : users)
						userIds.add(((JsonObject) userObj).getString("id", ""));
					getUsersPreferences(userIds, new Handler<JsonArray>(){
						public void handle(JsonArray preferences) {
							for(Object userObj : preferences){
								final JsonObject userPrefs = (JsonObject) userObj;
								final String userDomain = userPrefs.getString("lastDomain", I18n.DEFAULT_DOMAIN);
								final String userScheme = userPrefs.getString("lastScheme", "http");
								String mutableUserLanguage = "fr";
								try {
									mutableUserLanguage = new JsonObject(userPrefs.getString("language", "{}")).getString("default-domain", "fr");
								} catch(Exception e) {
									log.error("UserId [" + userPrefs.getString("userId", "") + "] - Bad language preferences format");
								}
								final String userLanguage = mutableUserLanguage;

								getUserNotifications(userPrefs.getString("userId", ""), dayDate.getTime(), new Handler<JsonArray>(){
									public void handle(JsonArray notifications) {
										if(notifications.size() == 0){
											usersEndHandler.handle(null);
											return;
										}

										SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", Locale.forLanguageTag(userLanguage));
										final JsonArray dates = new JsonArray();
										final JsonArray templates = new JsonArray();

										for(Object notificationObj : notifications){
											JsonObject notification = (JsonObject) notificationObj;
											final String notificationName =
													notification.getString("type","").toLowerCase() + "." +
															notification.getString("event-type", "").toLowerCase();
											if(notificationsDefaults.getObject(notificationName) == null)
												continue;

											JsonObject notificationPreference = userPrefs
													.getObject("preferences", new JsonObject())
													.getObject("config", new JsonObject())
													.getObject(notificationName, new JsonObject());
											if(TimelineNotificationsLoader.Frequencies.DAILY.name().equals(
													notificationPrefsMixin("defaultFrequency", notificationPreference, notificationsDefaults.getObject(notificationName))) &&
													!TimelineNotificationsLoader.Restrictions.INTERNAL.name().equals(
															notificationPrefsMixin("restriction", notificationPreference, notificationsDefaults.getObject(notificationName))) &&
													!TimelineNotificationsLoader.Restrictions.HIDDEN.name().equals(
															notificationPrefsMixin("restriction", notificationPreference, notificationsDefaults.getObject(notificationName)))){
												templates.add(new JsonObject()
														.putString("template", notificationsDefaults.getObject(notificationName, new JsonObject()).getString("template", ""))
														.putObject("params", notification.getObject("params", new JsonObject())));
												dates.add(formatter.format(MongoDb.parseIsoDate(notification.getObject("date"))));
											}
										}
										if(templates.size() > 0){
											JsonObject templateParams = new JsonObject()
													.putArray("nestedTemplatesArray", templates)
													.putArray("notificationDates", dates);
											processTimelineTemplate(templateParams, "", "notifications/daily-mail.html",
													userDomain, userScheme, userLanguage, false, new Handler<String>() {
														public void handle(final String processedTemplate) {
															//On completion : log
															final Handler<Message<JsonObject>> completionHandler = new Handler<Message<JsonObject>>(){
																public void handle(Message<JsonObject> event) {
																	if("error".equals(event.body().getString("status", "error"))){
																		log.error("[Timeline daily emails] Error while sending mail : " + event.body());
																		results.putNumber("users.ko", results.getInteger("users.ko") + 1);
																	} else {
																		results.putNumber("mails.sent", results.getInteger("mails.sent") + 1);
																	}
																	usersEndHandler.handle(null);
																}

															};

															//Translate mail title
															JsonArray keys = new JsonArray()
																	.add("timeline.daily.mail.subject.header");
															translateTimeline(keys, userDomain, userLanguage, new Handler<JsonArray>() {
																public void handle(JsonArray translations) {
																	//Send mail containing the "daily" notifications
																	emailSender.sendEmail(request,
																			userPrefs.getString("userMail", ""),
																			null,
																			null,
																			translations.get(0).toString(),
																			processedTemplate,
																			null,
																			false,
																			completionHandler);
																}
															});
														}
													});
										} else {
											usersEndHandler.handle(null);
										}

									}
								});
							}
						}
					});
				}
			};

			public void handle(Boolean continuation) {
				if(continuation){
					getImpactedUsers(notifiedUsers, userPagination.getAndIncrement(), new Handler<Either<String,JsonArray>>() {
						public void handle(Either<String, JsonArray> event) {
							if(event.isLeft()){
								log.error("[sendDailyMails] Error while retrieving impacted users : " + event.left().getValue());
								handler.handle(new Either.Left<String, JsonObject>(event.left().getValue()));
							} else {
								JsonArray users = event.right().getValue();
								usersHandler.handle(users);
							}
						}
					});
				} else {
					handler.handle(new Either.Right<String, JsonObject>(results));
				}
			}
		};

		getRecipientsUsers(dayDate.getTime(), new Handler<JsonArray>() {
			@Override
			public void handle(JsonArray event) {
				if (event != null && event.size() > 0) {
					notifiedUsers.addAll(event.toList());
					endPage.set((event.size() / USERS_LIMIT) + (event.size() % USERS_LIMIT != 0 ? 1 : 0));
				} else {
					handler.handle(new Either.Right<String, JsonObject>(results));
					return;
				}
				getNotificationsDefaults(new Handler<JsonArray>() {
					public void handle ( final JsonArray notifications){
						if (notifications == null) {
							log.error("[sendDailyMails] Error while retrieving notifications defaults.");
						} else {
							for (Object notifObj : notifications) {
								final JsonObject notif = (JsonObject) notifObj;
								notificationsDefaults.putObject(notif.getString("key", ""), notif);
							}
							userContinuationHandler.handle(true);
						}
					}
				});
			}
		});
	}

	public void sendWeeklyMails(int dayDelta, final Handler<Either<String, JsonObject>> handler) {

		final HttpServerRequest request = new JsonHttpServerRequest(new JsonObject());
		final AtomicInteger userPagination = new AtomicInteger(0);
		final AtomicInteger endPage = new AtomicInteger(0);
		final Calendar weekDate = Calendar.getInstance();
		weekDate.add(Calendar.DAY_OF_MONTH, dayDelta - 6);
		weekDate.set(Calendar.HOUR_OF_DAY, 0);
		weekDate.set(Calendar.MINUTE, 0);
		weekDate.set(Calendar.SECOND, 0);
		weekDate.set(Calendar.MILLISECOND, 0);

		final JsonObject results = new JsonObject()
				.putNumber("mails.sent", 0)
				.putNumber("users.ko", 0);
		final JsonObject notificationsDefaults = new JsonObject();
		final List<String> notifiedUsers = new ArrayList<>();

		final Handler<Boolean> userContinuationHandler = new Handler<Boolean>() {

			private final Handler<Boolean> continuation = this;
			private final Handler<JsonArray> usersHandler = new Handler<JsonArray>() {
				public void handle(final JsonArray users) {
					final int nbUsers = users.size();
					if (nbUsers == 0) {
						log.info("[WeeklyMails] Page0 : " + userPagination.get() + "/" + endPage.get());
						continuation.handle(userPagination.get() != endPage.get());
						return;
					}
					final AtomicInteger usersCountdown = new AtomicInteger(nbUsers);

					final VoidHandler usersEndHandler = new VoidHandler() {
						protected void handle() {
							if (usersCountdown.decrementAndGet() <= 0) {
								log.info("[WeeklyMails] Page : " + userPagination.get() + "/" + endPage.get());
								continuation.handle(userPagination.get() != endPage.get());
							}
						}
					};

					final JsonArray userIds = new JsonArray();
					for (Object userObj : users)
						userIds.add(((JsonObject) userObj).getString("id", ""));
					getUsersPreferences(userIds, new Handler<JsonArray>() {
						public void handle(JsonArray preferences) {
							for (Object userObj : preferences) {
								final JsonObject userPrefs = (JsonObject) userObj;
								final String userDomain = userPrefs.getString("lastDomain", I18n.DEFAULT_DOMAIN);
								final String userScheme = userPrefs.getString("lastScheme", "http");
								String mutableUserLanguage = "fr";
								try {
									mutableUserLanguage = new JsonObject(userPrefs.getString("language", "{}")).getString("default-domain", "fr");
								} catch (Exception e) {
									log.error("UserId [" + userPrefs.getString("userId", "") + "] - Bad language preferences format");
								}
								final String userLanguage = mutableUserLanguage;

								getAggregatedUserNotifications(userPrefs.getString("userId", ""), weekDate.getTime(), new Handler<JsonArray>() {
									public void handle(JsonArray notifications) {
										if (notifications.size() == 0) {
											usersEndHandler.handle(null);
											return;
										}

										final JsonArray weeklyNotifications = new JsonArray();

										for (Object notificationObj : notifications) {
											JsonObject notification = (JsonObject) notificationObj;
											final String notificationName =
													notification.getString("type", "").toLowerCase() + "." +
															notification.getString("event-type", "").toLowerCase();
											if (notificationsDefaults.getObject(notificationName) == null)
												continue;

											JsonObject notificationPreference = userPrefs
													.getObject("preferences", new JsonObject())
													.getObject("config", new JsonObject())
													.getObject(notificationName, new JsonObject());
											if (TimelineNotificationsLoader.Frequencies.WEEKLY.name().equals(
													notificationPrefsMixin("defaultFrequency", notificationPreference, notificationsDefaults.getObject(notificationName))) &&
													!TimelineNotificationsLoader.Restrictions.INTERNAL.name().equals(
															notificationPrefsMixin("restriction", notificationPreference, notificationsDefaults.getObject(notificationName))) &&
													!TimelineNotificationsLoader.Restrictions.HIDDEN.name().equals(
															notificationPrefsMixin("restriction", notificationPreference, notificationsDefaults.getObject(notificationName)))) {
												notification.putString("notificationName", notificationName);
												weeklyNotifications.add(notification);
											}
										}

										final JsonObject weeklyNotificationsObj = new JsonObject();
										final JsonArray weeklyNotificationsGroupedArray = new JsonArray();
										for (Object notif : weeklyNotifications) {
											JsonObject notification = (JsonObject) notif;
											if (!weeklyNotificationsObj.containsField(notification.getString("type").toLowerCase()))
												weeklyNotificationsObj.putObject(notification.getString("type").toLowerCase(), new JsonObject()
														.putString("link", notificationsDefaults
																.getObject(notification.getString("notificationName")).getString("app-address", ""))
														.putArray("event-types", new JsonArray()));
											weeklyNotificationsObj
													.getObject(notification.getString("type").toLowerCase())
													.getArray(("event-types"), new JsonArray())
													.add(notification);
										}

										for (String key : weeklyNotificationsObj.toMap().keySet()) {
											weeklyNotificationsGroupedArray.add(new JsonObject()
													.putString("type", key)
													.putString("link", weeklyNotificationsObj.getObject(key).getString("link", ""))
													.putArray("event-types", weeklyNotificationsObj.getObject(key).getArray("event-types")));
										}

										if (weeklyNotifications.size() > 0) {
											JsonObject templateParams = new JsonObject().putArray("notifications", weeklyNotificationsGroupedArray);
											processTimelineTemplate(templateParams, "", "notifications/weekly-mail.html",
													userDomain, userScheme, userLanguage, false, new Handler<String>() {
														public void handle(final String processedTemplate) {
															//On completion : log
															final Handler<Message<JsonObject>> completionHandler = new Handler<Message<JsonObject>>() {
																public void handle(Message<JsonObject> event) {
																	if ("error".equals(event.body().getString("status", "error"))) {
																		log.error("[Timeline weekly emails] Error while sending mail : " + event.body());
																		results.putNumber("users.ko", results.getInteger("users.ko") + 1);
																	} else {
																		results.putNumber("mails.sent", results.getInteger("mails.sent") + 1);
																	}
																	usersEndHandler.handle(null);
																}

															};

															//Translate mail title
															JsonArray keys = new JsonArray()
																	.add("timeline.weekly.mail.subject.header");
															translateTimeline(keys, userDomain, userLanguage, new Handler<JsonArray>() {
																public void handle(JsonArray translations) {
																	//Send mail containing the "weekly" notifications
																	emailSender.sendEmail(request,
																			userPrefs.getString("userMail", ""),
																			null,
																			null,
																			translations.get(0).toString(),
																			processedTemplate,
																			null,
																			false,
																			completionHandler);
																}
															});
														}
													});
										} else {
											usersEndHandler.handle(null);
										}

									}
								});
							}
						}
					});
				}
			};

			public void handle(Boolean continuation) {
				if (continuation) {
					getImpactedUsers(notifiedUsers, userPagination.getAndIncrement(), new Handler<Either<String, JsonArray>>() {
						public void handle(Either<String, JsonArray> event) {
							if (event.isLeft()) {
								log.error("[sendWeeklyMails] Error while retrieving impacted users : " + event.left().getValue());
								handler.handle(new Either.Left<String, JsonObject>(event.left().getValue()));
							} else {
								JsonArray users = event.right().getValue();
								usersHandler.handle(users);
							}
						}
					});
				} else {
					handler.handle(new Either.Right<String, JsonObject>(results));
				}
			}
		};
		getRecipientsUsers(weekDate.getTime(), new Handler<JsonArray>() {
			@Override
			public void handle(JsonArray event) {
				if (event != null && event.size() > 0) {
					notifiedUsers.addAll(event.toList());
					endPage.set((event.size() / USERS_LIMIT) + (event.size() % USERS_LIMIT != 0 ? 1 : 0));
				} else {
					handler.handle(new Either.Right<String, JsonObject>(results));
					return;
				}
				getNotificationsDefaults(new Handler<JsonArray>() {
					public void handle(final JsonArray notifications) {
						if (notifications == null) {
							log.error("[sendWeeklyMails] Error while retrieving notifications defaults.");
						} else {
							for (Object notifObj : notifications) {
								final JsonObject notif = (JsonObject) notifObj;
								notificationsDefaults.putObject(notif.getString("key", ""), notif);
							}
							userContinuationHandler.handle(true);
						}
					}
				});

			}
		});
	}

		@Override
	public void getNotificationsDefaults(final Handler<JsonArray> handler) {
		configService.list(new Handler<Either<String, JsonArray>>() {
			public void handle(Either<String, JsonArray> event) {
				if (event.isLeft()) {
					handler.handle(null);
				} else {
					JsonArray config = event.right().getValue();

					JsonArray notificationsList = new JsonArray();
					for (String key : registeredNotifications.keySet()) {
						JsonObject notif = new JsonObject(registeredNotifications.get(key));
						notif.putString("key", key);
						for (Object notifConfigObj : config) {
							JsonObject notifConfig = (JsonObject) notifConfigObj;
							if (notifConfig.getString("key", "").equals(key)) {
								notif.putString("defaultFrequency",
										notifConfig.getString("defaultFrequency", notif.getString("defaultFrequency")));
								notif.putString("restriction",
										notifConfig.getString("restriction", notif.getString("restriction")));
								break;
							}
						}
						notificationsList.add(notif);
					}
					handler.handle(notificationsList);
				}
			}
		});
	}

	/**
	 * Retrieves stored timeline user preferences.
	 *
	 * @param userIds : Ids of the users
	 * @param handler : Handles the preferences
	 */
	private void getUsersPreferences(JsonArray userIds, final Handler<JsonArray> handler){
		eb.send(USERBOOK_ADDRESS, new JsonObject()
				.putString("action", "get.userlist")
				.putString("application", "timeline")
				.putString("additionalMatch", ", u-[:IN]->(g:Group)-[:AUTHORIZED]->(r:Role)-[:AUTHORIZE]->(act:WorkflowAction) ")
				.putString("additionalWhere", "AND act.name = \"org.entcore.timeline.controllers.TimelineController|mixinConfig\" ")
				.putString("additionalCollectFields", ", language: uac.language")
				.putArray("userIds", userIds), new Handler<Message<JsonObject>>() {
			public void handle(Message<JsonObject> event) {
				if (!"error".equals(event.body().getString("status"))) {
					handler.handle(event.body().getArray("results"));
				} else {
					handler.handle(null);
				}
			}
		});
	}

	/**
	 * Retrieves all timeline notifications from mongodb for a single user, from a specific date in the past.
	 *
	 * @param userId : Userid
	 * @param from : The starting date
	 * @param handler : Handles the notifications
	 */
	private void getUserNotifications(String userId, Date from, final Handler<JsonArray> handler){
		JsonObject matcher = MongoQueryBuilder.build(
				QueryBuilder
						.start("recipients").elemMatch(QueryBuilder.start("userId").is(userId).get())
						.and("date").greaterThanEquals(from));

		JsonObject keys = new JsonObject()
				.putNumber("_id", 0)
				.putNumber("type", 1)
				.putNumber("event-type", 1)
				.putNumber("params", 1)
				.putNumber("date", 1);
		mongo.find("timeline", matcher, null, keys, new Handler<Message<JsonObject>>() {
			public void handle(Message<JsonObject> event) {
				if("error".equals(event.body().getString("status", "error"))){
					handler.handle(new JsonArray());
				} else {
					handler.handle(event.body().getArray("results"));
				}
			}
		});
	}

	/**
	 * Returns either user preferences or defaults when the user has not chosen specific values.
	 *
	 * @param field : Which preference
	 * @param userPrefs : User preferences
	 * @param defaultPrefs : Default preferences
	 * @return The prevailing preference
	 */
	private String notificationPrefsMixin(String field, JsonObject userPrefs, JsonObject defaultPrefs){
		return userPrefs.getString(field, defaultPrefs.getString(field, ""));
	}

	private void getRecipientsUsers(Date from, final Handler<JsonArray> handler) {
		final JsonObject aggregation = new JsonObject();
		JsonArray pipeline = new JsonArray();
		aggregation
				.putString("aggregate", "timeline")
				.putBoolean("allowDiskUse", true)
				.putArray("pipeline", pipeline);

		JsonObject matcher = MongoQueryBuilder.build(QueryBuilder.start("date").greaterThanEquals(from));
		JsonObject grouper = new JsonObject("{ \"_id\" : \"notifiedUsers\", \"recipients\" : {\"$addToSet\" : \"$recipients.userId\"}}");

		pipeline.addObject(new JsonObject().putObject("$match", matcher));
		pipeline.add(new JsonObject().putString("$unwind", "$recipients"));
		pipeline.add(new JsonObject().putObject("$group", grouper));

		mongo.command(aggregation.toString(), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				if ("error".equals(event.body().getString("status", "error"))) {
					handler.handle(new JsonArray());
				} else {
					JsonArray r = event.body().getObject("result", new JsonObject()).getArray("result");
					if (r != null && r.size() > 0) {
						handler.handle(r.<JsonObject>get(0).getArray("recipients", new JsonArray()));
					} else {
						handler.handle(new JsonArray());
					}
				}
			}

		});
	}

	/**
	 * Retrieves users having an email address, paginated.
	 *
	 * @param page : Page number
	 * @param handler : Handles the users
	 */
	private void getImpactedUsers(List<String> recipients, int page, final Handler<Either<String, JsonArray>> handler){
		int fromIdx = page * USERS_LIMIT;
		int toIdx = page * USERS_LIMIT + USERS_LIMIT;
		if (fromIdx >= recipients.size()) {
			handler.handle(new Either.Right<String, JsonArray>(new JsonArray()));
			return;
		}
		if (toIdx > recipients.size()) {
			toIdx = recipients.size();
		}
		final String query =
				"MATCH (u:User)-[:IN]->(g:Group)-[:AUTHORIZED]->(r:Role)-[:AUTHORIZE]->(act:WorkflowAction) " +
				"WHERE u.id IN {notifiedUsers} AND u.activationCode IS NULL AND u.email IS NOT NULL AND length(u.email) > 0 " +
				"AND act.name = \"org.entcore.timeline.controllers.TimelineController|mixinConfig\"" +
				"RETURN DISTINCT u.email as mail, u.id as id ";
		JsonObject params = new JsonObject()
				.putArray("notifiedUsers", new JsonArray(recipients.subList(fromIdx, toIdx).toArray()));
		neo4j.execute(query, params, Neo4jResult.validResultHandler(handler));
	}

	/**
	 * Retrieves an aggregated list of notifications from mongodb for a single user.
	 *
	 *  Notifications are grouped by type & event-type.
	 * @param userId : Userid
	 * @param from : Starting date in the past
	 * @param handler: Handles the notifications
	 */
	private void getAggregatedUserNotifications(String userId, Date from, final Handler<JsonArray> handler){
		final JsonObject aggregation = new JsonObject();
		JsonArray pipeline = new JsonArray();
		aggregation
				.putString("aggregate", "timeline")
				.putBoolean("allowDiskUse", true)
				.putArray("pipeline", pipeline);

		JsonObject matcher = MongoQueryBuilder.build(
				QueryBuilder
						.start("recipients").elemMatch(QueryBuilder.start("userId").is(userId).get())
						.and("date").greaterThanEquals(from));
		JsonObject grouper = new JsonObject("{ \"_id\" : { \"type\": \"$type\", \"event-type\": \"$event-type\"}, \"count\": { \"$sum\": 1 } }");
		JsonObject transformer = new JsonObject("{ \"type\": \"$_id.type\", \"event-type\": \"$_id.event-type\", \"count\": 1, \"_id\": 0 }");

		pipeline.addObject(new JsonObject().putObject("$match", matcher));
		pipeline.add(new JsonObject().putObject("$group", grouper));
		pipeline.add(new JsonObject().putObject("$project", transformer));

		mongo.command(aggregation.toString(), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				if("error".equals(event.body().getString("status", "error"))){
					handler.handle(new JsonArray());
				} else {
					handler.handle(
							event.body().getObject("result", new JsonObject())
									.getArray("result", new JsonArray()));
				}
			}

		});
	}

	public void setConfigService(TimelineConfigService configService) {
		this.configService = configService;
	}

	public void setRegisteredNotifications(Map<String, String> registeredNotifications) {
		this.registeredNotifications = registeredNotifications;
	}

	public void setEventsI18n(ConcurrentMap<String, String> eventsI18n) {
		this.eventsI18n = eventsI18n;
	}

	public void setLazyEventsI18n(HashMap<String, JsonObject> lazyEventsI18n) {
		this.lazyEventsI18n = lazyEventsI18n;
	}
}
