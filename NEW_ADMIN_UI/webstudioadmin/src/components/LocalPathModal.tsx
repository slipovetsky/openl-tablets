import React, { useState } from 'react';
import { Button, Modal } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';

export const LocalPathModal:React.FC =() =>{
    const [isModalOpen, setIsModalOpen] = useState(false);

    const showModal = () => {
        setIsModalOpen(true);
    };

       const handleClose = () => {
        setIsModalOpen(false);
    };

    return (
        <>
            <InfoCircleOutlined onClick={showModal} />

            <Modal title="Details" open={isModalOpen} footer={[
          <Button key="back" onClick={handleClose}>
            OK
          </Button>]}>
                <p>
                    A local path to directory for Git repository. Webstudio uses this specified path to upload Git repository from the server and works with it.
                    <br></br><b>NOTE:</b> Read/Write rights for specified directory is mandatory for Webstudio.
                </p>
            </Modal>
        </>
    );
};