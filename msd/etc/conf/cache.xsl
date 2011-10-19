<!-- Transform the Cache in XML to XHTML human-readable
Juan Vera del Campo, 1-13-2005 -->
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns="http://www.w3.org/1999/xhtml">
	<xsl:output method="xml" encoding="utf-8" indent="yes"/>
	<xsl:output doctype-public="-//W3C//DTD XHTML 1.0 Strict//EN"/>
	<xsl:output
		doctype-system="http://www.w3c.org/TR/xhtml1/DTD/xhtml1-strict.dtd"/>

	<xsl:template match="cache">
<html>
	<head>
		<title>Cache <xsl:apply-templates select="@id"/></title>
		<style type="text/css">
			body{
				background-color: white; color: black;
				font-family: Arial, sans-serif, serif;
			}
			tr{ background-color: #aaa; }
			td{ margin: 0.1em; }
			.classes{ color: #00f; }
			.attrs{ color: #0a0; }
			.internet{ background-color: #aaf; }
			.bluetooth{ background-color: #faf; }
		</style>
	</head>
	<body>
		<h1>Cache <xsl:apply-templates select="@id"/></h1>
		<table>
			<tr><th>Name</th><th>Id</th><th>IdCache</th><th>Attributes</th><th>Security</th></tr>
			<xsl:apply-templates select="service"/>
		</table>
	</body>
</html>
	</xsl:template>

	<xsl:template match="service">
		<xsl:element name="tr">
			<xsl:attribute name="class">
				<xsl:apply-templates select="network/@name"/>
			</xsl:attribute>
			<td><xsl:apply-templates select="@name"/></td>
			<td><xsl:apply-templates select="@id"/></td>
			<td><xsl:apply-templates select="@idcache"/></td>
			<td><ul class="attrs"><xsl:apply-templates select="attr"/></ul></td>
			<td><ul class="classes">
				<li>Hops: <xsl:apply-templates
						select="@hops"/></li>
				<li>Confidence: <xsl:apply-templates
						select="@confidence"/></li>
			</ul></td>
		</xsl:element>
	</xsl:template>

	<xsl:template match="attr">
		<li><xsl:apply-templates select="@name"/>:
			<xsl:apply-templates/></li>
	</xsl:template>
</xsl:stylesheet>
