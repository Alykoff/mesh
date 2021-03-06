package com.gentics.mesh.graphql.type;

import com.gentics.mesh.core.data.ContainerType;
import com.gentics.mesh.core.data.NamedElement;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.node.NodeContent;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.data.schema.SchemaContainerVersion;
import com.gentics.mesh.core.rest.schema.SchemaModel;
import com.gentics.mesh.core.rest.schema.impl.SchemaModelImpl;
import com.gentics.mesh.graphql.context.GraphQLContext;
import com.gentics.mesh.graphql.filter.NodeFilter;
import com.gentics.mesh.json.JsonUtil;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;
import graphql.schema.GraphQLOutputType;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.gentics.mesh.graphql.type.NodeTypeProvider.NODE_PAGE_TYPE_NAME;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

@Singleton
public class SchemaTypeProvider extends AbstractTypeProvider {

	private static final Logger log = LoggerFactory.getLogger(SchemaTypeProvider.class);

	public static final String SCHEMA_TYPE_NAME = "Schema";

	public static final String SCHEMA_PAGE_TYPE_NAME = "SchemasPage";

	public static final String SCHEMA_FIELD_TYPE = "SchemaFieldType";

	@Inject
	public InterfaceTypeProvider interfaceTypeProvider;

	@Inject
	public SchemaTypeProvider() {
	}

	public GraphQLObjectType createType(GraphQLContext context) {
		Builder schemaType = newObject().name(SCHEMA_TYPE_NAME).description("Node schema");
		interfaceTypeProvider.addCommonFields(schemaType);

		schemaType.field(newFieldDefinition().name("name").type(GraphQLString).dataFetcher((env) -> {
			Object source = env.getSource();
			if (source instanceof NamedElement) {
				return ((NamedElement) source).getName();
			}
			return null;
		}));

//		schemaType.field(newPagingFieldWithFetcher("projects", "Projects that this schema is assigned to", (env) -> {
//			GraphQLContext gc = env.getContext();
//			SchemaContainer schema = env.getSource();
//			return schema.findReferencedBranches().keySet().stream().map(Branch::getProject).distinct()
//					.filter(it -> gc.getUser().hasPermission(it, GraphPermission.READ_PERM)).collect(Collectors.toList());
//		}, PROJECT_REFERENCE_PAGE_TYPE_NAME));

		schemaType.field(newFieldDefinition().name("isContainer").type(GraphQLBoolean).dataFetcher((env) -> {
			SchemaModel model = loadModelWithFallback(env);
			return model != null ? model.getContainer() : null;
		}));

		schemaType.field(newFieldDefinition().name("displayField").type(GraphQLString).dataFetcher((env) -> {
			SchemaModel model = loadModelWithFallback(env);
			return model != null ? model.getDisplayField() : null;
		}));

		schemaType.field(newFieldDefinition().name("segmentField").type(GraphQLString).dataFetcher((env) -> {
			SchemaModel model = loadModelWithFallback(env);
			return model != null ? model.getSegmentField() : null;
		}));

		schemaType
			.field(newPagingFieldWithFetcherBuilder("nodes", "Load nodes with this schema", env -> {
			GraphQLContext gc = env.getContext();
			List<String> languageTags = getLanguageArgument(env);

			Stream<? extends NodeContent> nodes = StreamSupport.stream(getSchemaContainerVersion(env).getNodes(
					gc.getBranch().getUuid(),
					gc.getUser(),
					ContainerType.forVersion(gc.getVersioningParameters().getVersion())
			).spliterator(), false)
			.map(node -> {
				NodeGraphFieldContainer container = node.findVersion(gc, languageTags);
				return new NodeContent(node, container, languageTags);
			});

			return applyNodeFilter(env, nodes);
		}, NODE_PAGE_TYPE_NAME).argument(NodeFilter.filter(context).createFilterArgument()));

		Builder fieldListBuilder = newObject().name(SCHEMA_FIELD_TYPE).description("List of schema fields");

		fieldListBuilder.field(newFieldDefinition().name("name").type(GraphQLString).description("Name of the field"));
		fieldListBuilder.field(newFieldDefinition().name("label").type(GraphQLString).description("Label of the field"));
		fieldListBuilder.field(newFieldDefinition().name("required").type(GraphQLBoolean).description("Whether this field is required"));
		fieldListBuilder.field(newFieldDefinition().name("type").type(GraphQLString).description("The type of the field"));
		// TODO add "allow" and "indexSettings"

		GraphQLOutputType type = GraphQLList.list(fieldListBuilder.build());

		schemaType.field(newFieldDefinition().name("fields").type(type).dataFetcher(env -> loadModelWithFallback(env).getFields()));

		return schemaType.build();
	}

	private SchemaContainerVersion getSchemaContainerVersion(DataFetchingEnvironment env) {
		Object source = env.getSource();
		if (source instanceof SchemaContainerVersion) {
			return (SchemaContainerVersion) source;
		} else if (source instanceof SchemaContainer) {
			return ((SchemaContainer) source).getLatestVersion();
		} else {
			throw new RuntimeException("Invalid type {" + source + "}.");
		}
	}

	private SchemaModel loadModelWithFallback(DataFetchingEnvironment env) {
		Object source = env.getSource();
		if (source instanceof SchemaContainer) {
			SchemaContainer schema = env.getSource();
			SchemaModel model = JsonUtil.readValue(schema.getLatestVersion().getJson(), SchemaModelImpl.class);
			return model;
		}
		if (source instanceof SchemaContainerVersion) {
			SchemaContainerVersion schema = env.getSource();
			SchemaModel model = JsonUtil.readValue(schema.getJson(), SchemaModelImpl.class);
			return model;
		}
		log.error("Invalid type {" + source + "}.");
		return null;
	}
}
