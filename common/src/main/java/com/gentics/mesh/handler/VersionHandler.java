package com.gentics.mesh.handler;

import com.gentics.mesh.core.rest.error.GenericRestException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.gentics.mesh.core.rest.error.Errors.error;

@Singleton
public class VersionHandler implements Handler<RoutingContext> {
	public static final int CURRENT_API_VERSION = 2;
	public static final String VERSION_CONTEXT_KEY = "apiversion";
	public static final String API_MOUNTPOINT = String.format("/api/:%s/*", VERSION_CONTEXT_KEY);

	private static final Pattern versionRegex = Pattern.compile("^v(\\d+)$");

	@Inject
	public VersionHandler() {}

	@Override
	public void handle(RoutingContext event) {
		int version = parseVersion(event);
		event.put(VERSION_CONTEXT_KEY, version);
		event.next();
	}

	private int parseVersion(RoutingContext event) {
		String strVersion = event.pathParam(VERSION_CONTEXT_KEY);
		Matcher matcher = versionRegex.matcher(strVersion);
		matcher.find();
		try {
			int version = Integer.parseInt(matcher.group(1));
			if (version < 1 || version > CURRENT_API_VERSION) {
				throw notFoundError(strVersion);
			}
			return version;
		} catch (RuntimeException ex) {
			throw notFoundError(strVersion);
		}
	}

	private GenericRestException notFoundError(String strVersion) {
		return error(HttpResponseStatus.NOT_FOUND, "error_version_not_found", strVersion, "v" + CURRENT_API_VERSION);
	}

	public static String baseRoute(int version) {
		return "/api/v" + version;
	}

	public Stream<String> generateVersionMountpoints() {
		return IntStream.rangeClosed(1, CURRENT_API_VERSION)
			.mapToObj(VersionHandler::baseRoute);
	}
}
