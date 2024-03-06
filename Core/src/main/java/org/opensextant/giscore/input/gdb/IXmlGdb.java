/****************************************************************************************
 *  IXmlGdb.java
 *
 *  Created: Feb 6, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.input.gdb;

/**
 * This interface contains all the values and element tags for creating the ESRI
 * Gdb Xml interchange document format.
 * 
 * @author DRAND
 */
public interface IXmlGdb {
	// Values
    String ESRI = "esri";
	String ESRI_NS = "http://www.esri.com/schemas/ArcGIS/10.1";
	String XS = "xs";
	String XS_NS = "http://www.w3.org/2001/XMLSchema";
	String XSI = "xsi";
	String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
	
	// XSD Schema attributes
    String TYPE_ATTR = "type";
	
	// Generated Field Names
    String GEO_FIELD = "geometry";

	// Elements 
    String ALIAS_NAME = "AliasName";
	String ARRAY_OF_POINT = "ArrayOfPoint";
	String AREA_FIELD_NAME = "AreaFieldName";
	String AVG_NUM_POINTS = "AvgNumPoints";
	String CAN_VERSION = "CanVersion";
	String CATALOG_PATH = "CatalogPath";
	String CHILDREN_EXPANDED = "ChildrenExpanded";
	String CONFIGURATION_KEYWORD = "ConfigurationKeyword";
	String CONTROLLER_MEMBERSHIPS = "ControllerMemberships";
	String CLSID = "CLSID";
	String DATA = "Data";
	String DATA_ELEMENT = "DataElement";
	String DATASET_DATA = "DatasetData";
	String DATASET_DEFS = "DatasetDefinitions";
	String DATASET_NAME = "DatasetName";
	String DATASET_TYPE = "DatasetType";
	String DOMAIN = "Domain";
	String DOMAINS = "Domains";
	String DSID = "DSID";
	String EDITABLE = "Editable";
	String EXTCLSID = "EXTCLSID";
	String EXTENT = "Extent";
	String EXT_PROPS = "ExtensionProperties";
	String FEATURE_TYPE = "FeatureType";
	String FIELD = "Field";
	String FIELD_ARRAY = "FieldArray";
	String FIELDS = "Fields";
	String FROM_POINT = "FromPoint";
	String GEOMETRY_DEF = "GeometryDef";
	String GEOMETRY_TYPE = "GeometryType";
	String GLOBAL_ID_FIELD = "GlobalIDFieldName";
	String GRID_SIZE = "GridSize";
	String HAS_GLOBAL_ID = "HasGlobalID";
	String HAS_ID = "HasID";
	String HAS_M = "HasM";
	String HAS_OID = "HasOID";
	String HAS_SPATIAL_INDEX = "HasSpatialIndex";
	String HAS_Z = "HasZ";
	String HIGH_PRECISION = "HighPrecision";
	String INDEX = "Index";
	String INDEXES = "Indexes";
	String INDEX_ARRAY = "IndexArray";
	String IS_ASCENDING = "IsAscending";
	String IS_NULLABLE = "IsNullable";
	String IS_UNIQUE = "IsUnique";
	String LEFT_LONGITUDE = "LeftLongitude";
	String LENGTH = "Length";
	String LENGTH_FIELD_NAME = "LengthFieldName";
	String METADATA = "Metadata";
	String METADATA_RETRIEVED = "MetadataRetrieved";
	String MODEL_NAME = "ModelName";
	String MULTIPOINT_N = "MultipointN";
	String M_ORIGIN = "MOrigin";
	String M_SCALE = "MScale";
	String M_TOLERANCE = "MTolerance";
	String NAME = "Name";
	String OID_FIELD_NAME = "OIDFieldName";
	String PATH = "Path";
	String PATH_ARRAY = "PathArray";
	String POINT = "Point";
	String POINT_ARRAY = "PointArray";
	String POINT_N = "PointN";
	String PRECISION = "Precision";
	String PROPERTY_ARRAY = "PropertyArray";
	String RASTER_FIELD_NAME = "RasterFieldName";
	String RECORD = "Record";
	String RECORDS = "Records";
	String REL_CLASS_NAMES = "RelationshipClassNames";
	String REQUIRED = "Required";
	String RING = "Ring";
	String RING_ARRAY = "RingArray";
	String SCALE = "Scale";
	String SHAPE_FIELD_NAME = "ShapeFieldName";
	String SHAPE_TYPE = "ShapeType";
	String SPATIAL_REFERENCE = "SpatialReference";
	String TABLE_DATA = "TableData";
	String TABLE_ROLE = "TableRole";
	String TO_POINT = "ToPoint";
	String VALUE = "Value";
	String VALUES = "Values";
	String VERSION = "Version";
	String VERSIONED = "Versioned";
	String WKID = "WKID";
	String WKT = "WKT";
	String WORKSPACE = "Workspace";
	String WORKSPACE_DATA = "WorkspaceData";
	String WORKSPACE_DEF = "WorkspaceDefinition";
	String WORKSPACE_TYPE = "WorkspaceType";
	String X = "X";
	String X_ORIGIN = "XOrigin";
	String XMAX = "XMax";
	String XMIN = "XMin";
	String XMLDOC = "XmlDoc";
	String XY_SCALE = "XYScale";
	String XY_TOLERANCE = "XYTolerance";
	String Y = "Y";
	String YMAX = "YMax";
	String YMIN = "YMin";
	String Y_ORIGIN = "YOrigin";
	String Z_ORIGIN = "ZOrigin";
	String Z_SCALE = "ZScale";
	String Z_TOLERANCE = "ZTolerance";
}
