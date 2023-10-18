// https://github.com/JostAusSoest/jsDeyeInverter/blob/main/code.groovy
metadata
{
  definition(name:'jsDeyeInverter', namespace:'de.schwider', author:'Jost Schwider', description:'Just Simple Deye Inverter Driver')
  {
    capability 'Polling'
    capability 'PowerMeter'
    
    attribute 'installedAt','String'
    attribute 'updatedAt',  'String'
    attribute 'isPolling',  'Boolean'
    attribute 'power',      'Number'
    attribute 'yieldToday', 'Number'
    attribute 'yieldTotal', 'Number'
    attribute 'maxToday',   'Number'
    attribute 'maxTotal',   'Number'
    
    command 'resetMaxTotal'
  }
  
  preferences
  {
    input 'ipDevice',    'text',     title:'IP address:',     required:true,
                                     description:'<small>' + (ipDevice ? "<a href='http://$ipDevice' target=_blank>Open Deye Inverter GUI</a> (needs power>0)" : 'E.g.: 192.168.178.66') + '</small>'
    input 'user',        'text',     title:'Login user:',     required:true, defaultValue:'admin'
    input 'password',    'password', title:'Login password:', required:true, defaultValue:'admin'
    input 'autoPolling', 'bool',     title:'Auto polling',
                                     description:'<small>Repeat poll every 5 minutes?<br />Necessary for regular power measurement.</small>'
    input 'logReport',   'bool',     title:'Log Report'
    input 'logWarnings', 'bool',     title:'Log Warnings'
	}
}


public installed()
{
  log.info 'installed: ' + device
  sendEvent name:'installedAt', value:tsFormat()
}


public void updated()
{
  log.info 'updated: ' + device
  state.clear ()
  sendEvent name:'updatedAt', value:tsFormat()
  runIn 1, 'initPoller'
}


public void parse(def s)
{
  poll ()
}


public void resetMaximum()
{
  sendEvent name:'maxTotal', value:device.currentPower, unit:'W'
}


// capability 'Polling': ====================


public void poll()
{
  // Neuer Tag?
  String ts = tsFormat()
  if (ts[0..9] != device.currentUpdatedAt[0..9])
  {
    sendEvent name:'updatedAt', value:ts
    sendEvent name:'yieldToday', value:0, unit:'kWh'
    sendEvent name:'maxToday', value:0, unit:'W'
  }
  
  // Aktuellen Status holen:
  String s
  use(groovy.time.TimeCategory)
  {
    // Abfrage nur, wenn Sonne aufgegangen (mit 30 Minuten Reserve):
    def currTime = new Date()
    if (location.sunrise - 30.minutes < currTime && currTime < location.sunset + 30.minutes)
      s = requestStatus ()
  }
  
  // Aktuelle Werte auslesen:
  Integer newPower = s ? value(s, 'webdata_now_p') : 0
  if (newPower != device.currentPower)
  {
    sendEvent name:'updatedAt', value:ts
    sendEvent name:'power', value:newPower, unit:'W'
    if (newPower > 0)
    {
      sendEvent name:'yieldToday', value:value(s, 'webdata_today_e'), unit:'kWh'
      sendEvent name:'yieldTotal', value:value(s, 'webdata_total_e'), unit:'kWh'
      if (newPower > device.currentMaxToday)
        sendEvent name:'maxToday', value:newPower, unit:'W'
      if (newPower > device.currentMaxTotal)
        sendEvent name:'maxTotal', value:newPower, unit:'W'
    }
  }
}


public void initPoller()
{
  unschedule ()
  if (autoPolling) runEvery5Minutes 'poll'
  sendEvent name:'isPolling', value:autoPolling
  poll ()
}


// Interface Hubitat/DeyeInverter: ====================


private String requestStatus()
{
  String url = 'http://' + ipDevice + '/status.html'
  String s = httpRequest(url)
  
  if (logReport && s) log.info '<small>requestStatus: ' + s + '</small>'
  
  return s
}


private float value(String s, String name, Float defaultValue = 0)
{
  String v = substring(s, name + ' = "', '";')
  if (v)
    return (Float.parseFloat(v) * 10).intValue() / 10
  else
    return defaultValue
}


private String httpRequest(String url)
{
  def s = null
  try
  {
    Map headers = [Authorization:'Basic ' + ("$user:$password").bytes.encodeBase64()]
    Map params = [uri:url, headers:headers, timeout:15]
    httpGet(params)
    {
      if (it.success) s = it.data
    }
  }
  catch (Exception e)
  {
    if (logWarnings) log.warn 'httpRequest: ' + e
    return null
  }
  return s.toString()
}


// Utilities: ====================


private String tsFormat(String format = 'yyyy/MM/dd HH:mm:ss', Date ts = null)
{
  if (! ts) ts = new Date()
  return ts.format(format)
}


private String substring(String s, String prefix, String postfix)
{
  int i = s.indexOf(prefix)
  if (i < 0) return null
  i += prefix.length()
  
  int j = s.indexOf(postfix, i)
  if (j < 0) return null
  
  return s.substring(i, j)
}
