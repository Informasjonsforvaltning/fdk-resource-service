-- Rename graph format configuration columns to shorter names
ALTER TABLE union_graphs 
    RENAME COLUMN graph_format TO format;
    
ALTER TABLE union_graphs 
    RENAME COLUMN graph_style TO style;
    
ALTER TABLE union_graphs 
    RENAME COLUMN graph_expand_uris TO expand_uris;

