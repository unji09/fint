import asyncio
from functools import partial
from typing import Protocol, runtime_checkable

import boto3

from app.core.errors import BusinessException, CommonErrorCode


@runtime_checkable
class S3Client(Protocol):
    async def get_object(self, key: str) -> bytes: ...


class BotoS3Client:
    def __init__(self, bucket: str, region: str, access_key: str, secret_key: str) -> None:
        self._bucket = bucket
        self._client = boto3.client(
            "s3",
            region_name=region,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
        )

    async def get_object(self, key: str) -> bytes:
        try:
            resp = await asyncio.to_thread(
                partial(self._client.get_object, Bucket=self._bucket, Key=key)
            )
            return await asyncio.to_thread(resp["Body"].read)
        except Exception as e:
            raise BusinessException(
                CommonErrorCode.EXTERNAL_API_FAILED, f"S3 get_object failed: {e}"
            ) from e
