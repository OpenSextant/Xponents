def fast_replace(val, chars, sep):
    """ Many to one replace    Replace all such chars in val with a separator, sep
    Example:
         f= Some Obscure Filename (2010.12.4).ppt   ## Not suitable for some applications
         after fastReplace( f, ' (.', '_' )

            Some_Obscure_Filename__2010_12_4__ppt
    """
    retok = StringIO()
    for ch in val:
        if ch in chars:
            retok.write(sep)
        else:
            retok.write(ch)
    reval = retok.getvalue()
    retok.close()
    return reval