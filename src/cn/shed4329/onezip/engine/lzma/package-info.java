/**
 * LZMA/LZMA2 stream implementations and bounded independent-block encoding workers. Encoding
 * workspaces are reusable but must not be used by multiple encoders concurrently. Stream and pool
 * lifetimes are owned by an archive operation, not by a global executor.
 */
package cn.shed4329.onezip.engine.lzma;
