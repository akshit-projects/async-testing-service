package ab.async.tester.controllers

import com.google.inject.{Inject, Provider, Singleton}
import play.api.mvc._
import play.api.routing.Router

import scala.jdk.CollectionConverters._

@Singleton
class OpenAPIController @Inject()(
  cc: ControllerComponents,
  routerProvider: Provider[Router]
)(implicit ec: scala.concurrent.ExecutionContext) extends AbstractController(cc) {

  /**
   * Get OpenAPI specification
   * GET /api/v1/openapi.json
   */
  def getOpenAPISpec: Action[AnyContent] = Action { implicit request =>
    val spec = generateOpenAPISpec()
    Ok(spec).as("application/json")
  }

  /**
   * Serve Swagger UI
   * GET /api/v1/docs
   */
  def swaggerUI: Action[AnyContent] = Action { implicit request =>
    val html =
      """
        |<!DOCTYPE html>
        |<html lang="en">
        |<head>
        |  <meta charset="UTF-8">
        |  <title>Async Testing Service API Documentation</title>
        |  <link rel="stylesheet" type="text/css" href="https://unpkg.com/swagger-ui-dist@5.10.0/swagger-ui.css" />
        |  <style>
        |    html { box-sizing: border-box; overflow: -moz-scrollbars-vertical; overflow-y: scroll; }
        |    *, *:before, *:after { box-sizing: inherit; }
        |    body { margin:0; padding:0; }
        |  </style>
        |</head>
        |<body>
        |  <div id="swagger-ui"></div>
        |  <script src="https://unpkg.com/swagger-ui-dist@5.10.0/swagger-ui-bundle.js"></script>
        |  <script src="https://unpkg.com/swagger-ui-dist@5.10.0/swagger-ui-standalone-preset.js"></script>
        |  <script>
        |    window.onload = function() {
        |      window.ui = SwaggerUIBundle({
        |        url: "/api/v1/openapi.json",
        |        dom_id: '#swagger-ui',
        |        deepLinking: true,
        |        presets: [
        |          SwaggerUIBundle.presets.apis,
        |          SwaggerUIStandalonePreset
        |        ],
        |        plugins: [
        |          SwaggerUIBundle.plugins.DownloadUrl
        |        ],
        |        layout: "StandaloneLayout"
        |      });
        |    };
        |  </script>
        |</body>
        |</html>
      """.stripMargin
    
    Ok(html).as("text/html")
  }

  private def generateOpenAPISpec(): String = {
    // Extract routes from Play router
    val routes = routerProvider.get().documentation
    
    // Group routes by path
    val groupedRoutes = routes.groupBy(_._2)
    
    // Build paths object
    val paths = groupedRoutes.map { case (path, routeList) =>
      val operations = routeList.map { case (method, _, _) =>
        val operationId = s"${method.toLowerCase}_${path.replaceAll("[^a-zA-Z0-9]", "_")}"
        val summary = s"$method $path"
        
        s"""
           |    "${method.toLowerCase}": {
           |      "operationId": "$operationId",
           |      "summary": "$summary",
           |      "responses": {
           |        "200": {
           |          "description": "Successful response"
           |        }
           |      }
           |    }
         """.stripMargin
      }.mkString(",")
      
      s"""
         |  "${path.replaceAll("\\$", "")}": {
         |$operations
         |  }
       """.stripMargin
    }.mkString(",")
    
    // Generate OpenAPI JSON
    s"""{
       |  "openapi": "3.0.3",
       |  "info": {
       |    "title": "Async Testing Service API",
       |    "description": "API for managing async testing flows, resources, test suites, and executions",
       |    "version": "1.0.0",
       |    "contact": {
       |      "name": "API Support"
       |    }
       |  },
       |  "servers": [
       |    {
       |      "url": "http://localhost:9000",
       |      "description": "Local development server"
       |    }
       |  ],
       |  "paths": {
       |$paths
       |  },
       |  "components": {
       |    "securitySchemes": {
       |      "bearerAuth": {
       |        "type": "http",
       |        "scheme": "bearer",
       |        "bearerFormat": "JWT"
       |      }
       |    },
       |    "schemas": {
       |      "Organisation": {
       |        "type": "object",
       |        "properties": {
       |          "id": { "type": "string" },
       |          "name": { "type": "string" },
       |          "createdAt": { "type": "integer", "format": "int64" },
       |          "modifiedAt": { "type": "integer", "format": "int64" }
       |        }
       |      },
       |      "Team": {
       |        "type": "object",
       |        "properties": {
       |          "id": { "type": "string" },
       |          "orgId": { "type": "string" },
       |          "name": { "type": "string" },
       |          "createdAt": { "type": "integer", "format": "int64" },
       |          "modifiedAt": { "type": "integer", "format": "int64" }
       |        }
       |      },
       |      "TestSuite": {
       |        "type": "object",
       |        "properties": {
       |          "id": { "type": "string" },
       |          "name": { "type": "string" },
       |          "description": { "type": "string" },
       |          "creator": { "type": "string" },
       |          "flows": { "type": "array", "items": { "$$ref": "#/components/schemas/TestSuiteFlowConfig" } },
       |          "runUnordered": { "type": "boolean" },
       |          "enabled": { "type": "boolean" },
       |          "orgId": { "type": "string" },
       |          "teamId": { "type": "string" },
       |          "createdAt": { "type": "integer", "format": "int64" },
       |          "modifiedAt": { "type": "integer", "format": "int64" }
       |        }
       |      },
       |      "TestSuiteFlowConfig": {
       |        "type": "object",
       |        "properties": {
       |          "flowId": { "type": "string" },
       |          "version": { "type": "integer" },
       |          "parameters": { "type": "object", "additionalProperties": { "type": "string" } }
       |        }
       |      },
       |      "Error": {
       |        "type": "object",
       |        "properties": {
       |          "error": { "type": "string" }
       |        }
       |      }
       |    }
       |  },
       |  "security": [
       |    {
       |      "bearerAuth": []
       |    }
       |  ]
       |}""".stripMargin
  }
}

